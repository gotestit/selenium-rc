package WWW::Selenium::XMLParseUtils;
use strict;
use warnings;
use base 'Exporter';
our @EXPORT_OK = qw/html2pod strip_blockquotes extract_functions/;

sub strip_blockquotes {
    my $xml = shift;
    while ($xml =~ m#(<blockquote>\n*)(.*?)(\n*</blockquote>)#s) {
        my ($start_bq, $new_text, $ending_bq) = ($1, $2, $3);
        if ($new_text =~ /<blockquote>/) {
            # found a nested blockquote!  Eek!
            $new_text = strip_blockquotes($new_text . $ending_bq);
            $new_text = "$start_bq$new_text\n";
#            warn "new_text=($new_text)";
        }
        else {
            my @lines = split "\n", $new_text;
            $new_text = "\n=over\n\n"
                       . join("\n", @lines)
                       . "\n\n=back\n\n";

            # hack for Element Filters section - it's inconsistent with the
            # other sections, and shouldn't be in a blockquote 
            # Remove this once fixed
            if ($lines[0] =~ m/^Element filters can be used/) {
                $new_text = "\n" . join("\n", @lines) . "\n";
            }

        }

        $xml =~ s#<blockquote>.*?</blockquote>\n?#$new_text#s or die;
#        warn "xml=($xml)";
    }
    return $xml;
}

sub html2pod {
    my $text = shift;
    $text =~ s#^<p>([^<]+)</p>$#\n$1\n#smg;             # p's should be spaced out a bit
    $text =~ s#^</?(?:p|dl)>##smg;                      # <p>s and <dl>s on their own line
    $text =~ s#</?(?:p|dl)>##g;                         # <p>s and <dl>s 
    $text =~ s#<a name="[^"]+">([^<]*)</a>#$1#g;        # don't need anchors
    $text =~ s#<h3>([^<]+)</h3>#=head3 $1#g;            # headings
    $text =~ s#<em>([^<]+)</em>#I<$1>#g;                # italics
    $text =~ s#<strong>([^<]+)</strong>#B<$1>#g;        # italics
    $text =~ s#</?dd>#\n#gs;                            # item text
    $text =~ s#<dt>(.+?)</dt>#=item $1\n#g;             # list items
    $text =~ s#<li>(.+?)</li>#=item $1\n\n#g;           # ul item
    $text =~ s#<ul[^>]+>#\n=over\n\n#g;                 # ul start
    $text =~ s#</ul>#=back\n\n#g;                       # ul end
    $text =~ s/<a href="#[^"]+">([^<]+)<\/a>/$1/g;      # strip in-page links
    $text =~ s#<a href="([^"]+)">([^<]+)</a>#$1 ($2)#g; # reformat links
    $text =~ s#<code>([^<]+)</code>#C<$1>#g;            # code blocks

    # Un-escape characters
    $text =~ s/&#(\d+);/sprintf('%c', $1)/eg;
    $text =~ s/&quot;/"/g;

    return $text;
}

sub extract_functions {
    my $text = shift;
    my @functions;
    while ($text =~ s#<function name="([^"]+)">\n*(.+?)\n*</function>##s) {
        my ($name, $desc) = ($1, $2);
        my $perl_name = camel2perl($name);

        die "Nested function: ($desc)" if $desc =~ /<function>/;
        my ($return_type, $return_desc) = _extract_return_type($desc);
        my $params = _extract_params($desc);
        $desc =~ m#<comment>(.+?)</comment>#s;
        my $func_comment = $1 or die "Can't find function comment: $desc";

        my $sel_func = $return_type ? "get_$return_type" : "do_command";
        my $return   = $return_type ? "return " : '';
        push @functions, { name => $perl_name, text => <<EOT };
=item \$sel-E<gt>$perl_name($params->{names})

$func_comment

$params->{desc}
$return_desc
=cut

sub $perl_name {
    my \$self = shift;
    $return\$self->$sel_func("$name", \@_);
}

EOT
    }

    return map { $_ =~ s#\n{2,}#\n\n#g; $_ } # strip newlines
             map { html2pod($_->{text}) } # get rid of any <p>'s
#             sort { $a->{name} cmp $b->{name} }
               @functions;
}

sub _extract_params {
    my $text = shift;
    my @params;
    while ($text =~ s#<param name="([^"]+)">([^<]+)</param>\n*##) {
        my ($name, $desc) = ($1, $2);
        $name = camel2perl($name);
        push @params, { name => $name, desc => $desc };
    }
    my $names = join ', ', 
                  map { "\$$_->{name}" } 
                    @params;
    my $desc = '';
    if (@params) {
        $desc = "=over\n\n"
                . join("\n\n", 
                    map { "\$$_->{name} is $_->{desc}" } 
                      @params)
                . "\n\n=back\n";
    }
    return { names => $names, desc => $desc };
}

sub _extract_return_type {
    if ($_[0] =~ s/<return type="([^"]+)">([^<]+)<\/return>\n*//) {
        my ($type, $desc) = ($1, $2);
        $type =~ s/\[\]/_array/;
        return ($type, "\n=over\n\nReturns $desc\n\n=back\n\n");
    }
    return ('', '');
}

sub camel2perl { 
    my $n = shift; 
    $n =~ s/([a-z]+)([A-Z]+)/"$1_" . lc($2)/eg; 
    return $n;
}

1;
