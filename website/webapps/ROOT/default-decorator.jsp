<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3c.org/TR/1999/REC-html401-19991224/loose.dtd">
<%@ taglib uri="sitemesh-decorator" prefix="decorator" %>
<%@ taglib uri="sitemesh-page" prefix="page" %>
<HTML>
<HEAD>
	<title><decorator:title /></title>
	<META http-equiv=Content-Type content="text/html; charset=windows-1252">
	<LINK href="index_files/selenium.css" type=text/css rel=stylesheet>
	<META <decorator:head />
</HEAD>
<BODY>
<DIV class=header>
	<table width="98%" border="0" cellpadding="0" cellspacing="0">
		<tr>
 			<td><IMG ALT="Selenium" SRC="index_files/selenium3.jpg"></td>
			<td align="right" valign="top"><a href="http://www.thoughtworks.com"><img src="index_files/Logo_trans_white.gif" width="100"
			height="20" border="0" alt="ThoughtWorks Logo"/></a></td>
		</tr>
	</table>
</DIV>
<DIV class="container">&nbsp;
	<table align="right" border="0" cellpadding="2" cellspacing="0">
		<tr>
			<td class="smalltext" align="left"><img src="index_files/icon_printer.gif" border="0" alt="Printer"/>
			&nbsp<a href="<%= request.getRequestURI() %>?printable=true">Printer friendly form of this page</a></td>
		</tr>
		<tr>
			<td>&nbsp;</td>
		</tr>
		<tr>
			<td class="newshead" align="left"><u>Latest News:</u></td>
		</tr>
		<tr>
			<td class="newstext" align="left">May 20, 2005<br>&nbsp;&nbsp;- New <A href="http://selenium.thoughtworks.com/download.html">0.4 Downloads</a> available</td>
		</tr>
		<tr>
			<td class="newstext" align="left">May 2, 2005<br>&nbsp;&nbsp;- New <A href="http://selenium.thoughtworks.com/download.html">0.3 Downloads</a> available</td>
		</tr>
		<tr>
			<td class="newstext" align="left">Jan 24, 2005<br>&nbsp;&nbsp;- New <A href="http://selenium.thoughtworks.com/download.html">0.2 Downloads</a> available</td>
		</tr>
		<tr>
			<td class="newstext" align="left">Dec 14, 2004<br>&nbsp;&nbsp;- FAQ's are now on <A href="http://confluence.public.thoughtworks.org/display/SEL/FAQ">Selenium Confluence</a></td>
		</tr>
		<tr>
			<td class="newstext" align="left">Dec 4, 2004<br>&nbsp;&nbsp;- Selenium v0.1.3 available for <a href="http://gforge.public.thoughtworks.org/project/showfiles.php?group_id=1028">download</a></td>
		</tr>
		<tr>
			<td class="newstext" align="left">Nov 19, 2004<br>&nbsp;&nbsp;- New <A href="contact.html">mailing</a> lists created</td>
		</tr>
	</table>

    <page:applyDecorator name="panel" page="/index.html"/>

	<DIV class="content">
		<font class="pagetitle"><decorator:title /></font>
		<h6 class="pagetitle"></h6>
		<p>
<%
if (request.getRequestURI().indexOf("/index.html") != -1) {
%>
        <page:applyDecorator name="panel" page="/home-page.html" />
<%
  } else {
%>
		<decorator:body />
<%
  }
%>
	</DIV>
	<br>
	<DIV class="smalltext">
		<table width="100%" border="0" cellpadding="0" cellspacing="0">
			<hr>
			<tr>
 				<td align="center" valign="center"> &copy;2004 <a href="http://www.thoughtworks.com">
				ThoughtWorks, Inc.</a></td>
			</tr>
		</table>
	</DIV>
</DIV>
</BODY>
</HTML>



