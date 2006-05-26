package Test::WWW::Selenium;

use strict;
use base qw(WWW::Selenium);

our $VERSION = '0.21';

=head1 NAME

Test::WWW::Selenium - Test applications using Selenium Remote Control

=head1 SYNOPSIS

Test::WWW::Selenium is a subclass of WWW::Selenium that provides
convenient testing functions.

    use Test::More tests => 5;
    use Test::WWW::Selenium;

    # Parameters are passed through to 
    my $sel = Test::WWW::Selenium->new( host => "localhost", 
                                        port => 4444, 
                                        browser => "*firefox", 
                                        browser_url => "http://www.google.com",
                                      );

    # use special test wrappers around WWW::Selenium commands:
    $sel->open_ok "http://www.google.com";
    $sel->type_ok "q", "hello world";
    $sel->click_ok "btnG";
    $sel->wait_for_page_to_load 5000;
    $sel->title_like, qr/Google Search/;
                                        
=head1 REQUIREMENTS

To use this module, you need to have already downloaded and started the
Selenium Server.  (The Selenium Server is a Java application.)

=head1 DESCRIPTION

This module is a C<WWW::Selenium> subclass providing some methods
useful for writing tests. For each Selenium command (open, click,
type, ...) there is a corresponding <command>_ok method that
checks the return value (open_ok, click_ok, type_ok).

For each Selenium getter (get_title, ...) there are six autogenerated
methods (<getter>_is, <getter>_isnt, <getter>_like, <getter>_unlike,
<getter>_contains, <getter>_lacks) to check the value of the
attribute.

You can use bot Java-style (openOk, titleIs, titleLacks) and
Perl-style (open_ok, title_is, title_lacks) in method names.

Perl style is, of course, recommended.

=cut

use Test::LongString;
use Test::More;
use Test::Builder;

our $AUTOLOAD;

my $Test = Test::Builder->new;

my %comparator = (
    is       => 'is_string',
    isnt     => 'isnt',
    like     => 'like_string',
    unlike   => 'unlike_string',
    contains => 'contains_string',
    lacks    => 'lacks_string'
);

# These commands don't require a locator
# grep item lib/WWW/Selenium.pm | grep sel | grep \(\) | grep get
my %no_locator = map { $_ => 1 }
                qw(alert confirmation prompt location
                   title body_text all_buttons all_links all_fields);

sub AUTOLOAD {
    my $name = $AUTOLOAD;
    $name =~ s/.*:://;
    return if $name eq 'DESTROY';

    my $sub;
    if ($name =~ /(\w+)_(is|isnt|like|unlike|contains|lacks)$/i) {
        my $getter = "get_$1";
        my $comparator = $comparator{lc $2};

        # make a subroutine that will call Test::Builder's test methods
        # with selenium data from the getter
	if ($no_locator{$1}) {
            $sub = sub {
                my( $self, $str, $desc ) = @_;
                diag "Test::WWW::Selenium running $name (@_[1..$#_])"
                    if $self->{verbose};
                local $Test::Builder::Level = $Test::Builder::Level + 1;
                no strict 'refs';
                return &$comparator( $self->$getter, $str, $desc );
            };
        }
        else {
            $sub = sub {
                my( $self, $locator, $str, $desc ) = @_;
                diag "Test::WWW::Selenium running $name (@_[1..$#_])"
                    if $self->{verbose};
                local $Test::Builder::Level = $Test::Builder::Level + 1;
                no strict 'refs';
                return &$comparator( $self->$getter($locator), $str, $desc );
            };
        }
    } 
    elsif ($name =~ /(\w+)_ok$/i) {
        my $cmd = $1;

        # make a subroutine for ok() around the selenium command
        $sub = sub {
            my( $self, $arg1, $arg2, $desc ) = @_;
            diag "Test::WWW::Selenium running $name (@_[1..$#_])"
                    if $self->{verbose};

            local $Test::Builder::Level = $Test::Builder::Level + 1;
            return ok( $self->$cmd( $arg1, $arg2 ), $desc );
        };
    }

    # jump directly to the new subroutine, avoiding an extra frame stack
    if ($sub) {
        no strict 'refs';
        *{$AUTOLOAD} = $sub;
        goto &$AUTOLOAD;
    } 
    else {
        # pass through to WWW::Selenium
        $WWW::Selenium::AUTOLOAD = $AUTOLOAD;
        goto &WWW::Selenium::AUTOLOAD;
    }
}

sub new {
    my $class = shift;
    my $self = $class->SUPER::new(@_);
    $self->start;
    return $self;
}

sub DESTROY {
    my $self = shift;
    $self->stop;
}

1;

__END__

=head1 AUTHORS

Maintained by Luke Closs <lukec@cpan.org>

Originally by Mattia Barbon <mbarbon@cpan.org>

=head1 LICENSE

Copyright (c) 2006 Luke Closs <lukec@cpan.org>
Copyright (c) 2005,2006 Mattia Barbon <mbarbon@cpan.org>

This program is free software; you can redistribute it and/or
modify it under the same terms as Perl itself
