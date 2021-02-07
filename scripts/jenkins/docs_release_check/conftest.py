import pytest


@pytest.fixture
def release_index():
    return """<!DOCTYPE html>
    <html lang="en-us">
    <head>
        
    <meta charset="UTF-8">
    <title>Release notes | APM Java Agent Reference [1.x] | Elastic</title>
    <link rel="home" href="index.html" title="APM Java Agent Reference [1.x]"/>
    <link rel="up" href="index.html" title="APM Java Agent Reference [1.x]"/>
    <link rel="prev" href="upgrading.html" title="Upgrading"/>
    <link rel="next" href="_unreleased.html" title="Unreleased"/>
    <meta name="DC.type" content="Learn/Docs/APM Java Agent/Reference/1.x"/>
    <meta name="DC.subject" content="APM"/>
    <meta name="DC.identifier" content="1.x"/>

        <meta http-equiv="content-type" content="text/html; charset=utf-8" />
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="apple-touch-icon" sizes="57x57" href="/apple-icon-57x57.png">
        <link rel="apple-touch-icon" sizes="60x60" href="/apple-icon-60x60.png">
        <link rel="apple-touch-icon" sizes="72x72" href="/apple-icon-72x72.png">
        <link rel="apple-touch-icon" sizes="76x76" href="/apple-icon-76x76.png">
        <link rel="apple-touch-icon" sizes="114x114" href="/apple-icon-114x114.png">
        <link rel="apple-touch-icon" sizes="120x120" href="/apple-icon-120x120.png">
        <link rel="apple-touch-icon" sizes="144x144" href="/apple-icon-144x144.png">
        <link rel="apple-touch-icon" sizes="152x152" href="/apple-icon-152x152.png">
        <link rel="apple-touch-icon" sizes="180x180" href="/apple-icon-180x180.png">
        <link rel="icon" type="image/png" href="/favicon-32x32.png" sizes="32x32">
        <link rel="icon" type="image/png" href="/android-chrome-192x192.png" sizes="192x192">
        <link rel="icon" type="image/png" href="/favicon-96x96.png" sizes="96x96">
        <link rel="icon" type="image/png" href="/favicon-16x16.png" sizes="16x16">
        <link rel="manifest" href="/manifest.json">
        <meta name="apple-mobile-web-app-title" content="Elastic">
        <meta name="application-name" content="Elastic">
        <meta name="msapplication-TileColor" content="#ffffff">
        <meta name="msapplication-TileImage" content="/mstile-144x144.png">
        <meta name="theme-color" content="#ffffff">
        <meta name="naver-site-verification" content="936882c1853b701b3cef3721758d80535413dbfd" />
        <meta name="yandex-verification" content="d8a47e95d0972434" />
        <meta name="localized" content="true" />
        <meta name="st:robots" content="follow,index" />
        <meta property="og:image" content="https://www.elastic.co/static/images/elastic-logo-200.png" />
        <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
        <link rel="icon" href="/favicon.ico" type="image/x-icon">
        <link rel="apple-touch-icon-precomposed" sizes="64x64" href="/favicon_64x64_16bit.png">
        <link rel="apple-touch-icon-precomposed" sizes="32x32" href="/favicon_32x32.png">
        <link rel="apple-touch-icon-precomposed" sizes="16x16" href="/favicon_16x16.png">
        <!-- Give IE8 a fighting chance -->
        <!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js"></script>
        <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
        <![endif]-->
        <link rel="stylesheet" type="text/css" href="/guide/static/styles.css" />
    </head>

    <body>
        <!-- Google Tag Manager -->
        <script>dataLayer = [];</script><noscript><iframe src="//www.googletagmanager.com/ns.html?id=GTM-58RLH5" height="0" width="0" style="display:none;visibility:hidden"></iframe></noscript>
        <script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start': new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0], j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src= '//www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f); })(window,document,'script','dataLayer','GTM-58RLH5');</script>
        <!-- End Google Tag Manager -->
        
        <!-- Global site tag (gtag.js) - Google Analytics -->
        <script async src="https://www.googletagmanager.com/gtag/js?id=UA-12395217-16"></script>
        <script>
        window.dataLayer = window.dataLayer || [];
        function gtag(){dataLayer.push(arguments);}
        gtag('js', new Date());
        gtag('config', 'UA-12395217-16');
        </script>

        <!--BEGIN QUALTRICS WEBSITE FEEDBACK SNIPPET-->
        <script type='text/javascript'>
        (function(){var g=function(e,h,f,g){
        this.get=function(a){for(var a=a+"=",c=document.cookie.split(";"),b=0,e=c.length;b<e;b++){for(var d=c[b];" "==d.charAt(0);)d=d.substring(1,d.length);if(0==d.indexOf(a))return d.substring(a.length,d.length)}return null};
        this.set=function(a,c){var b="",b=new Date;b.setTime(b.getTime()+6048E5);b="; expires="+b.toGMTString();document.cookie=a+"="+c+b+"; path=/; "};
        this.check=function(){var a=this.get(f);if(a)a=a.split(":");else if(100!=e)"v"==h&&(e=Math.random()>=e/100?0:100),a=[h,e,0],this.set(f,a.join(":"));else return!0;var c=a[1];if(100==c)return!0;switch(a[0]){case "v":return!1;case "r":return c=a[2]%Math.floor(100/c),a[2]++,this.set(f,a.join(":")),!c}return!0};
        this.go=function(){if(this.check()){var a=document.createElement("script");a.type="text/javascript";a.src=g;document.body&&document.body.appendChild(a)}};
        this.start=function(){var a=this;window.addEventListener?window.addEventListener("load",function(){a.go()},!1):window.attachEvent&&window.attachEvent("onload",function(){a.go()})}};
        try{(new g(100,"r","QSI_S_ZN_emkP0oSe9Qrn7kF","https://znemkp0ose9qrn7kf-elastic.siteintercept.qualtrics.com/WRSiteInterceptEngine/?Q_ZID=ZN_emkP0oSe9Qrn7kF")).start()}catch(i){}})();
        </script><div id='ZN_emkP0oSe9Qrn7kF'><!--DO NOT REMOVE-CONTENTS PLACED HERE--></div>
        <!--END WEBSITE FEEDBACK SNIPPET-->

        <div id='elastic-nav' style="display:none;"></div>
        <script src='https://www.elastic.co/elastic-nav.js'></script>

        <!-- Subnav -->
        <div>
        <div>
            <div class="tertiary-nav d-none d-md-block">
            <div class="container">
                <div class="p-t-b-15 d-flex justify-content-between nav-container">
                <div class="breadcrum-wrapper"><span><a href="/guide/" style="font-size: 14px; font-weight: 600; color: #000;">Docs</a></span></div>
                </div>
            </div>
            </div>
        </div>
        </div>

        <div class="main-container">
        <section id="content" >
            <div class="content-wrapper">

            <section id="guide" lang="en">
                <div class="container">
                <div class="row">
                    <div class="col-xs-12 col-sm-8 col-md-8 guide-section">
                    <!-- start body -->
                    
    <div id="content">
    <div class="breadcrumbs">
    <span class="breadcrumb-link"><a href="index.html">APM Java Agent Reference [1.x]</a></span>
    »
    <span class="breadcrumb-node">Release notes</span>
    </div>
    <div class="navheader">
    <span class="prev">
    <a href="upgrading.html">« Upgrading</a>
    </span>
    <span class="next">
    <a href="_unreleased.html">Unreleased »</a>
    </span>
    </div>
    <div class="chapter">
    <div class="titlepage"><div><div>
    <h1 class="title"><a id="release-notes"></a>Release notes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/docs/release-notes.asciidoc">edit</a></h1>
    </div></div></div>
    <p>All notable changes to this project will be documented here.</p>
    <div class="ulist itemizedlist">
    <ul class="itemizedlist">
    <li class="listitem">
    <a class="xref" href="release-notes-1.x.html" title="Java Agent version 1.x">Java Agent version 1.x</a>
    </li>
    <li class="listitem">
    <a class="xref" href="release-notes-0.8.x.html" title="Java Agent version 0.8.x">Java Agent version 0.8.x</a>
    </li>
    <li class="listitem">
    <a class="xref" href="release-notes-0.7.x.html" title="Java Agent version 0.7.x">Java Agent version 0.7.x</a>
    </li>
    </ul>
    </div>




    </div>
    <div class="navfooter">
    <span class="prev">
    <a href="upgrading.html">« Upgrading</a>
    </span>
    <span class="next">
    <a href="_unreleased.html">Unreleased »</a>
    </span>
    </div>
    </div>

                    <!-- end body -->
                    </div>
                    <div class="col-xs-12 col-sm-4 col-md-4" id="right_col">
                    <div id="rtpcontainer" style="display: block;">
                        <div class="mktg-promo">
                        <h3>Most Popular</h3>
                        <ul class="icons">
                            <li class="icon-elasticsearch-white"><a href="https://www.elastic.co/webinars/getting-started-elasticsearch?baymax=default&elektra=docs&storm=top-video">Get Started with Elasticsearch: Video</a></li>
                            <li class="icon-kibana-white"><a href="https://www.elastic.co/webinars/getting-started-kibana?baymax=default&elektra=docs&storm=top-video">Intro to Kibana: Video</a></li>
                            <li class="icon-logstash-white"><a href="https://www.elastic.co/webinars/introduction-elk-stack?baymax=default&elektra=docs&storm=top-video">ELK for Logs & Metrics: Video</a></li>
                        </ul>
                        </div>
                    </div>
                    </div>
                </div>
                </div>
            </section>

            </div>


    <div id='elastic-footer'></div>
    <script src='https://www.elastic.co/elastic-footer.js'></script>
    <!-- Footer Section end-->

        </section>
        </div>

    <script type="text/javascript">
        var suggestionsUrl = "https://search.elastic.co/suggest";
        var localeUrl = '{"relative_url_prefix":"/","code":"en-us","display_code":"en-us","url":"/guide_template"}';
    </script>
    <script src="/static/js/swiftype_app_search.umd.min.js"></script>
    <script src="/guide/static/jquery.js"></script>
    <script type="text/javascript" src="/guide/static/docs.js"></script>
    <script type="text/javascript">
    window.initial_state = {}</script>
    </body>
    </html>
    """

@pytest.fixture
def release_sub():
    return """
    <!DOCTYPE html>
<html lang="en-us">
  <head>
    
<meta charset="UTF-8">
<title>Java Agent version 1.x | APM Java Agent Reference [1.x] | Elastic</title>
<link rel="home" href="index.html" title="APM Java Agent Reference [1.x]"/>
<link rel="up" href="release-notes.html" title="Release notes"/>
<link rel="prev" href="_unreleased.html" title="Unreleased"/>
<link rel="next" href="release-notes-0.8.x.html" title="Java Agent version 0.8.x"/>
<meta name="DC.type" content="Learn/Docs/APM Java Agent/Reference/1.x"/>
<meta name="DC.subject" content="APM"/>
<meta name="DC.identifier" content="1.x"/>

    <meta http-equiv="content-type" content="text/html; charset=utf-8" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="apple-touch-icon" sizes="57x57" href="/apple-icon-57x57.png">
    <link rel="apple-touch-icon" sizes="60x60" href="/apple-icon-60x60.png">
    <link rel="apple-touch-icon" sizes="72x72" href="/apple-icon-72x72.png">
    <link rel="apple-touch-icon" sizes="76x76" href="/apple-icon-76x76.png">
    <link rel="apple-touch-icon" sizes="114x114" href="/apple-icon-114x114.png">
    <link rel="apple-touch-icon" sizes="120x120" href="/apple-icon-120x120.png">
    <link rel="apple-touch-icon" sizes="144x144" href="/apple-icon-144x144.png">
    <link rel="apple-touch-icon" sizes="152x152" href="/apple-icon-152x152.png">
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-icon-180x180.png">
    <link rel="icon" type="image/png" href="/favicon-32x32.png" sizes="32x32">
    <link rel="icon" type="image/png" href="/android-chrome-192x192.png" sizes="192x192">
    <link rel="icon" type="image/png" href="/favicon-96x96.png" sizes="96x96">
    <link rel="icon" type="image/png" href="/favicon-16x16.png" sizes="16x16">
    <link rel="manifest" href="/manifest.json">
    <meta name="apple-mobile-web-app-title" content="Elastic">
    <meta name="application-name" content="Elastic">
    <meta name="msapplication-TileColor" content="#ffffff">
    <meta name="msapplication-TileImage" content="/mstile-144x144.png">
    <meta name="theme-color" content="#ffffff">
    <meta name="naver-site-verification" content="936882c1853b701b3cef3721758d80535413dbfd" />
    <meta name="yandex-verification" content="d8a47e95d0972434" />
    <meta name="localized" content="true" />
    <meta name="st:robots" content="follow,index" />
    <meta property="og:image" content="https://www.elastic.co/static/images/elastic-logo-200.png" />
    <link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
    <link rel="icon" href="/favicon.ico" type="image/x-icon">
    <link rel="apple-touch-icon-precomposed" sizes="64x64" href="/favicon_64x64_16bit.png">
    <link rel="apple-touch-icon-precomposed" sizes="32x32" href="/favicon_32x32.png">
    <link rel="apple-touch-icon-precomposed" sizes="16x16" href="/favicon_16x16.png">
    <!-- Give IE8 a fighting chance -->
    <!--[if lt IE 9]>
    <script src="https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js"></script>
    <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->
    <link rel="stylesheet" type="text/css" href="/guide/static/styles.css" />
  </head>

  <body>
    <!-- Google Tag Manager -->
    <script>dataLayer = [];</script><noscript><iframe src="//www.googletagmanager.com/ns.html?id=GTM-58RLH5" height="0" width="0" style="display:none;visibility:hidden"></iframe></noscript>
    <script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start': new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0], j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src= '//www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f); })(window,document,'script','dataLayer','GTM-58RLH5');</script>
    <!-- End Google Tag Manager -->
    
    <!-- Global site tag (gtag.js) - Google Analytics -->
    <script async src="https://www.googletagmanager.com/gtag/js?id=UA-12395217-16"></script>
    <script>
      window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', 'UA-12395217-16');
    </script>

    <!--BEGIN QUALTRICS WEBSITE FEEDBACK SNIPPET-->
    <script type='text/javascript'>
      (function(){var g=function(e,h,f,g){
      this.get=function(a){for(var a=a+"=",c=document.cookie.split(";"),b=0,e=c.length;b<e;b++){for(var d=c[b];" "==d.charAt(0);)d=d.substring(1,d.length);if(0==d.indexOf(a))return d.substring(a.length,d.length)}return null};
      this.set=function(a,c){var b="",b=new Date;b.setTime(b.getTime()+6048E5);b="; expires="+b.toGMTString();document.cookie=a+"="+c+b+"; path=/; "};
      this.check=function(){var a=this.get(f);if(a)a=a.split(":");else if(100!=e)"v"==h&&(e=Math.random()>=e/100?0:100),a=[h,e,0],this.set(f,a.join(":"));else return!0;var c=a[1];if(100==c)return!0;switch(a[0]){case "v":return!1;case "r":return c=a[2]%Math.floor(100/c),a[2]++,this.set(f,a.join(":")),!c}return!0};
      this.go=function(){if(this.check()){var a=document.createElement("script");a.type="text/javascript";a.src=g;document.body&&document.body.appendChild(a)}};
      this.start=function(){var a=this;window.addEventListener?window.addEventListener("load",function(){a.go()},!1):window.attachEvent&&window.attachEvent("onload",function(){a.go()})}};
      try{(new g(100,"r","QSI_S_ZN_emkP0oSe9Qrn7kF","https://znemkp0ose9qrn7kf-elastic.siteintercept.qualtrics.com/WRSiteInterceptEngine/?Q_ZID=ZN_emkP0oSe9Qrn7kF")).start()}catch(i){}})();
    </script><div id='ZN_emkP0oSe9Qrn7kF'><!--DO NOT REMOVE-CONTENTS PLACED HERE--></div>
    <!--END WEBSITE FEEDBACK SNIPPET-->

    <div id='elastic-nav' style="display:none;"></div>
    <script src='https://www.elastic.co/elastic-nav.js'></script>

    <!-- Subnav -->
    <div>
      <div>
        <div class="tertiary-nav d-none d-md-block">
          <div class="container">
            <div class="p-t-b-15 d-flex justify-content-between nav-container">
              <div class="breadcrum-wrapper"><span><a href="/guide/" style="font-size: 14px; font-weight: 600; color: #000;">Docs</a></span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="main-container">
      <section id="content" >
        <div class="content-wrapper">

          <section id="guide" lang="en">
            <div class="container">
              <div class="row">
                <div class="col-xs-12 col-sm-8 col-md-8 guide-section">
                  <!-- start body -->
                  
<div id="content">
<div class="breadcrumbs">
<span class="breadcrumb-link"><a href="index.html">APM Java Agent Reference [1.x]</a></span>
»
<span class="breadcrumb-link"><a href="release-notes.html">Release notes</a></span>
»
<span class="breadcrumb-node">Java Agent version 1.x</span>
</div>
<div class="navheader">
<span class="prev">
<a href="_unreleased.html">« Unreleased</a>
</span>
<span class="next">
<a href="release-notes-0.8.x.html">Java Agent version 0.8.x »</a>
</span>
</div>
<div class="section">
<div class="titlepage"><div><div>
<h2 class="title"><a id="release-notes-1.x"></a>Java Agent version 1.x<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h2>
</div></div></div>
<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.15.0"></a>1.15.0 - 2020/03/27<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_breaking_changes_2"></a>Breaking changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
<p>Ordering of configuration sources has slightly changed, please review <a class="xref" href="configuration.html" title="Configuration"><em>Configuration</em></a>:</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
<code class="literal">elasticapm.properties</code> file now has higher priority over java system properties and environment variables,<br>
This change allows to change dynamic options values at runtime by editing file, previously values set in java properties
or environment variables could not be overriden, even if they were dynamic.
</li>
</ul>
</div>
</li>
<li class="listitem">
<p>Renamed some configuration options related to the experimental profiler-inferred spans feature (<a href="https://github.com/elastic/apm-agent-java/pull/1084" class="ulink" target="_top">#1084</a>):</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
<code class="literal">profiling_spans_enabled</code> &#8594; <code class="literal">profiling_inferred_spans_enabled</code>
</li>
<li class="listitem">
<code class="literal">profiling_sampling_interval</code> &#8594; <code class="literal">profiling_inferred_spans_sampling_interval</code>
</li>
<li class="listitem">
<code class="literal">profiling_spans_min_duration</code> &#8594; <code class="literal">profiling_inferred_spans_min_duration</code>
</li>
<li class="listitem">
<code class="literal">profiling_included_classes</code> &#8594; <code class="literal">profiling_inferred_spans_included_classes</code>
</li>
<li class="listitem">
<code class="literal">profiling_excluded_classes</code> &#8594; <code class="literal">profiling_inferred_spans_excluded_classes</code>
</li>
<li class="listitem">
Removed <code class="literal">profiling_interval</code> and <code class="literal">profiling_duration</code> (both are fixed to 5s now)
</li>
</ul>
</div>
</li>
</ul>
</div>
<h5><a id="_features_2"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Gracefully abort agent init when running on a known Java 8 buggy JVM <a href="https://github.com/elastic/apm-agent-java/pull/1075" class="ulink" target="_top">#1075</a>.
</li>
<li class="listitem">
Add support for <a class="xref" href="supported-technologies-details.html#supported-databases" title="Data Stores">Redis Redisson client</a>
</li>
<li class="listitem">
Makes <a class="xref" href="config-core.html#config-instrument" title="instrument ()"><code class="literal">instrument</code> (<span class="Admonishment Admonishment--change">
[<span class="Admonishment-version u-mono">1.0.0</span>]
<span class="Admonishment-detail">
Added in 1.0.0. Changing this value at runtime is possible since version 1.15.0
</span>
</span>)</a>, <a class="xref" href="config-core.html#config-trace-methods" title="trace_methods ()"><code class="literal">trace_methods</code> (<span class="Admonishment Admonishment--change">
[<span class="Admonishment-version u-mono">1.0.0</span>]
<span class="Admonishment-detail">
Added in 1.0.0. Changing this value at runtime is possible since version 1.15.0
</span>
</span>)</a>, and <a class="xref" href="config-core.html#config-disable-instrumentations" title="disable_instrumentations ()"><code class="literal">disable_instrumentations</code> (<span class="Admonishment Admonishment--change">
[<span class="Admonishment-version u-mono">1.0.0</span>]
<span class="Admonishment-detail">
Added in 1.0.0. Changing this value at runtime is possible since version 1.15.0
</span>
</span>)</a> dynamic.
Note that changing these values at runtime can slow down the application temporarily.
</li>
<li class="listitem">
Do not instrument Servlet API before 3.0 <a href="https://github.com/elastic/apm-agent-java/pull/1077" class="ulink" target="_top">#1077</a>
</li>
<li class="listitem">
Add support for API keys for apm backend authentication <a href="https://github.com/elastic/apm-agent-java/pull/1083" class="ulink" target="_top">#1083</a>
</li>
<li class="listitem">
Add support for <a class="xref" href="supported-technologies-details.html#supported-rpc-frameworks" title="RPC frameworks">gRPC</a> client &amp; server instrumentation <a href="https://github.com/elastic/apm-agent-java/pull/1019" class="ulink" target="_top">#1019</a>
</li>
<li class="listitem">
Deprecating <code class="literal">active</code> configuration option in favor of <code class="literal">recording</code>.
Setting <code class="literal">active</code> still works as it&#8217;s now an alias for <code class="literal">recording</code>.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_2"></a>Bug fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
When JAX-RS-annotated method delegates to another JAX-RS-annotated method, transaction name should include method A - <a href="https://github.com/elastic/apm-agent-java/pull/1062" class="ulink" target="_top">#1062</a>
</li>
<li class="listitem">
Fixed bug that prevented an APM Error from being created when calling <code class="literal">org.slf4j.Logger#error</code> - <a href="https://github.com/elastic/apm-agent-java/pull/1049" class="ulink" target="_top">#1049</a>
</li>
<li class="listitem">
Wrong address in JDBC spans for Oracle, MySQL and MariaDB when multiple hosts are configured - <a href="https://github.com/elastic/apm-agent-java/pull/1082" class="ulink" target="_top">#1082</a>
</li>
<li class="listitem">
Document and re-order configuration priorities <a href="https://github.com/elastic/apm-agent-java/pull/1087" class="ulink" target="_top">#1087</a>
</li>
<li class="listitem">
Improve heuristic for <code class="literal">service_name</code> when not set through config <a href="https://github.com/elastic/apm-agent-java/pull/1097" class="ulink" target="_top">#1097</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.14.0"></a>1.14.0 - 2020/03/04<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_3"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Support for the official <a href="https://www.w3.org/TR/trace-context" class="ulink" target="_top">W3C</a> <code class="literal">traceparent</code> and <code class="literal">tracestate</code> headers.<br>
  The agent now accepts both the <code class="literal">elastic-apm-traceparent</code> and the official <code class="literal">traceparent</code> header.
By default, it sends both headers on outgoing requests, unless <a class="xref" href="config-core.html#config-use-elastic-traceparent-header" title="use_elastic_traceparent_header ()"><code class="literal">use_elastic_traceparent_header</code></a> is set to false.
</li>
<li class="listitem">
Creating spans for slow methods with the help of the sampling profiler <a href="https://github.com/jvm-profiling-tools/async-profiler" class="ulink" target="_top">async-profiler</a>.
This is a low-overhead way of seeing which methods make your transactions slow and a replacement for the <code class="literal">trace_methods</code> configuration option.
See <a class="xref" href="supported-technologies-details.html#supported-java-methods" title="Java method monitoring">Java method monitoring</a> for more details
</li>
<li class="listitem">
Adding a Circuit Breaker to pause the agent when stress is detected on the system and resume when the stress is relieved.
See <a class="xref" href="tuning-and-overhead.html#circuit-breaker" title="Circuit Breaker">Circuit Breaker</a> and <a href="https://github.com/elastic/apm-agent-java/pull/1040" class="ulink" target="_top">#1040</a> for more info.
</li>
<li class="listitem">
<code class="literal">Span#captureException</code> and <code class="literal">Transaction#captureException</code> in public API return reported error id - <a href="https://github.com/elastic/apm-agent-java/pull/1015" class="ulink" target="_top">#1015</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_3"></a>Bug fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
java.lang.IllegalStateException: Cannot resolve type description for &lt;com.another.commercial.apm.agent.Class&gt; - <a href="https://github.com/elastic/apm-agent-java/pull/1037" class="ulink" target="_top">#1037</a>
</li>
<li class="listitem">
properly handle <code class="literal">java.sql.SQLException</code> for unsupported JDBC features <a href="https://github.com/elastic/apm-agent-java/pull/" class="ulink" target="_top">#1035</a> <a href="https://github.com/elastic/apm-agent-java/issues/1025" class="ulink" target="_top">#1025</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.13.0"></a>1.13.0 - 2020/02/11<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_4"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Add support for <a class="xref" href="supported-technologies-details.html#supported-databases" title="Data Stores">Redis Lettuce client</a>
</li>
<li class="listitem">
Add <code class="literal">context.message.age.ms</code> field for JMS message receiving spans and transactions - <a href="https://github.com/elastic/apm-agent-java/pull/970" class="ulink" target="_top">#970</a>
</li>
<li class="listitem">
Instrument log4j Logger#error(String, Throwable) (<a href="https://github.com/elastic/apm-agent-java/pull/919" class="ulink" target="_top">#919</a>) Automatically captures exceptions when calling <code class="literal">logger.error("message", exception)</code>
</li>
<li class="listitem">
Add instrumentation for external process execution through <code class="literal">java.lang.Process</code> and Apache <code class="literal">commons-exec</code> - <a href="https://github.com/elastic/apm-agent-java/pull/903" class="ulink" target="_top">#903</a>
</li>
<li class="listitem">
Add <code class="literal">destination</code> fields to exit span contexts - <a href="https://github.com/elastic/apm-agent-java/pull/976" class="ulink" target="_top">#976</a>
</li>
<li class="listitem">
Removed <code class="literal">context.message.topic.name</code> field - <a href="https://github.com/elastic/apm-agent-java/pull/993" class="ulink" target="_top">#993</a>
</li>
<li class="listitem">
Add support for Kafka clients - <a href="https://github.com/elastic/apm-agent-java/pull/981" class="ulink" target="_top">#981</a>
</li>
<li class="listitem">
Add support for binary <code class="literal">traceparent</code> header format (see the <a href="https://github.com/elastic/apm/blob/master/docs/agent-development.md#Binary-Fields" class="ulink" target="_top">spec</a>
for more details) - <a href="https://github.com/elastic/apm-agent-java/pull/1009" class="ulink" target="_top">#1009</a>
</li>
<li class="listitem">
Add support for log correlation for log4j and log4j2, even when not used in combination with slf4j.
See <a class="xref" href="supported-technologies-details.html#supported-logging-frameworks" title="Logging frameworks">Logging frameworks</a> for details.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_4"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fix parsing value of <code class="literal">trace_methods</code> configuration property <a href="https://github.com/elastic/apm-agent-java/pull/930" class="ulink" target="_top">#930</a>
</li>
<li class="listitem">
Workaround for <code class="literal">java.util.logging</code> deadlock <a href="https://github.com/elastic/apm-agent-java/pull/965" class="ulink" target="_top">#965</a>
</li>
<li class="listitem">
JMS should propagate traceparent header when transactions are not sampled <a href="https://github.com/elastic/apm-agent-java/pull/999" class="ulink" target="_top">#999</a>
</li>
<li class="listitem">
Spans are not closed if JDBC implementation does not support <code class="literal">getUpdateCount</code> <a href="https://github.com/elastic/apm-agent-java/pull/1008" class="ulink" target="_top">#1008</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.12.0"></a>1.12.0 - 2019/11/21<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_5"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
<p>JMS Enhancements <a href="https://github.com/elastic/apm-agent-java/pull/911" class="ulink" target="_top">#911</a>:</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Add special handling for temporary queues/topics
</li>
<li class="listitem">
<p>Capture message bodies of text Messages</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Rely on the existing <code class="literal">ELASTIC_APM_CAPTURE_BODY</code> agent config option (off by default).
</li>
<li class="listitem">
Send as <code class="literal">context.message.body</code>
</li>
<li class="listitem">
Limit size to 10000 characters. If longer than this size, trim to 9999 and append with ellipsis
</li>
</ul>
</div>
</li>
<li class="listitem">
Introduce the <code class="literal">ignore_message_queues</code> configuration to disable instrumentation (message tagging) for specific
queues/topics as suggested in <a href="https://github.com/elastic/apm-agent-java/pull/710" class="ulink" target="_top">#710</a>
</li>
<li class="listitem">
<p>Capture predefined message headers and all properties</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Rely on the existing <code class="literal">ELASTIC_APM_CAPTURE_HEADERS</code> agent config option.
</li>
<li class="listitem">
Send as <code class="literal">context.message.headers</code>
</li>
<li class="listitem">
Sanitize sensitive headers/properties based on the <code class="literal">sanitize_field_names</code> config option
</li>
</ul>
</div>
</li>
</ul>
</div>
</li>
<li class="listitem">
Added support for the MongoDB sync driver. See <a href="/guide/en/apm/agent/java/master/supported-technologies-details.html#supported-databases" class="ulink" target="_top">supported data stores</a>.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_5"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
JDBC regression- <code class="literal">PreparedStatement#executeUpdate()</code> and <code class="literal">PreparedStatement#executeLargeUpdate()</code> are not traced <a href="https://github.com/elastic/apm-agent-java/pull/918" class="ulink" target="_top">#918</a>
</li>
<li class="listitem">
When systemd cgroup driver is used, the discovered Kubernetes pod UID contains "_" instead of "-" <a href="https://github.com/elastic/apm-agent-java/pull/920" class="ulink" target="_top">#920</a>
</li>
<li class="listitem">
DB2 jcc4 driver is not traced properly <a href="https://github.com/elastic/apm-agent-java/pull/926" class="ulink" target="_top">#926</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.11.0"></a>1.11.0 - 2019/10/31<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_6"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Add the ability to configure a unique name for a JVM within a service through the
<a href="/guide/en/apm/agent/java/master/config-core.html#config-service-node-name" class="ulink" target="_top"><code class="literal">service_node_name</code></a>
config option]
</li>
<li class="listitem">
Add ability to ignore some exceptions to be reported as errors <a href="/guide/en/apm/agent/java/master/config-core.html#config-ignore-exceptions" class="ulink" target="_top">ignore_exceptions</a>
</li>
<li class="listitem">
Applying new logic for JMS <code class="literal">javax.jms.MessageConsumer#receive</code> so that, instead of the transaction created for the
polling method itself (ie from <code class="literal">receive</code> start to end), the agent will create a transaction attempting to capture
the code executed during actual message handling.
This logic is suitable for environments where polling APIs are invoked within dedicated polling threads.
This polling transaction creation strategy can be reversed through a configuration option (<code class="literal">message_polling_transaction_strategy</code>)
that is not exposed in the properties file by default.
</li>
<li class="listitem">
Send IP obtained through <code class="literal">javax.servlet.ServletRequest#getRemoteAddr()</code> in <code class="literal">context.request.socket.remote_address</code>
instead of parsing from headers <a href="https://github.com/elastic/apm-agent-java/pull/889" class="ulink" target="_top">#889</a>
</li>
<li class="listitem">
Added <code class="literal">ElasticApmAttacher.attach(String propertiesLocation)</code> to specify a custom properties location
</li>
<li class="listitem">
Logs message when <code class="literal">transaction_max_spans</code> has been exceeded <a href="https://github.com/elastic/apm-agent-java/pull/849" class="ulink" target="_top">#849</a>
</li>
<li class="listitem">
Report the number of affected rows by a SQL statement (UPDATE,DELETE,INSERT) in <em>affected_rows</em> span attribute <a href="https://github.com/elastic/apm-agent-java/pull/707" class="ulink" target="_top">#707</a>
</li>
<li class="listitem">
Add <a href="/guide/en/apm/agent/java/master/public-api.html#api-traced" class="ulink" target="_top"><code class="literal">@Traced</code></a> annotation which either creates a span or a transaction, depending on the context
</li>
<li class="listitem">
Report JMS destination as a span/transaction context field <a href="https://github.com/elastic/apm-agent-java/pull/906" class="ulink" target="_top">#906</a>
</li>
<li class="listitem">
Added <a href="/guide/en/apm/agent/java/master/config-jmx.html#config-capture-jmx-metrics" class="ulink" target="_top"><code class="literal">capture_jmx_metrics</code></a> configuration option
</li>
</ul>
</div>
<h5><a id="_bug_fixes_6"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
JMS creates polling transactions even when the API invocations return without a message
</li>
<li class="listitem">
Support registering MBeans which are added after agent startup
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.10.0"></a>1.10.0 - 2019/09/30<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_7"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Add ability to manually specify reported <a href="/guide/en/apm/agent/java/master/config-core.html#config-hostname" class="ulink" target="_top">hostname</a>
</li>
<li class="listitem">
Add support for <a href="/guide/en/apm/agent/java/master/supported-technologies-details.html#supported-databases" class="ulink" target="_top">Redis Jedis client</a>
</li>
<li class="listitem">
Add support for identifying target JVM to attach apm agent to using JMV property. See also the documentation of the <a href="/guide/en/apm/agent/java/master/setup-attach-cli.html#setup-attach-cli-usage-list" class="ulink" target="_top"><code class="literal">--include</code> and <code class="literal">--exclude</code> flags</a>
</li>
<li class="listitem">
Added <a href="/guide/en/apm/agent/java/master/config-jmx.html#config-capture-jmx-metrics" class="ulink" target="_top"><code class="literal">capture_jmx_metrics</code></a> configuration option
</li>
<li class="listitem">
Improve servlet error capture <a href="https://github.com/elastic/apm-agent-java/pull/812" class="ulink" target="_top">#812</a>
Among others, now also takes Spring MVC `@ExceptionHandler`s into account
</li>
<li class="listitem">
Instrument Logger#error(String, Throwable) <a href="https://github.com/elastic/apm-agent-java/pull/821" class="ulink" target="_top">#821</a>
Automatically captures exceptions when calling <code class="literal">logger.error("message", exception)</code>
</li>
<li class="listitem">
Easier log correlation with <a href="https://github.com/elastic/java-ecs-logging" class="ulink" target="_top">https://github.com/elastic/java-ecs-logging</a>. See <a href="/guide/en/apm/agent/java/master/log-correlation.html" class="ulink" target="_top">docs</a>.
</li>
<li class="listitem">
Avoid creating a temp agent file for each attachment <a href="https://github.com/elastic/apm-agent-java/pull/859" class="ulink" target="_top">#859</a>
</li>
<li class="listitem">
Instrument <code class="literal">View#render</code> instead of <code class="literal">DispatcherServlet#render</code> <a href="https://github.com/elastic/apm-agent-java/pull/829" class="ulink" target="_top">#829</a>
This makes the transaction breakdown graph more useful. Instead of <code class="literal">dispatcher-servlet</code>, the graph now shows a type which is based on the view name, for example, <code class="literal">FreeMarker</code> or <code class="literal">Thymeleaf</code>.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_7"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Error in log when setting <a href="/guide/en/apm/agent/java/current/config-reporter.html#config-server-urls" class="ulink" target="_top">server_urls</a>
to an empty string - <code class="literal">co.elastic.apm.agent.configuration.ApmServerConfigurationSource - Expected previousException not to be null</code>
</li>
<li class="listitem">
Avoid terminating the TCP connection to APM Server when polling for configuration updates <a href="https://github.com/elastic/apm-agent-java/pull/823" class="ulink" target="_top">#823</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.9.0"></a>1.9.0 - 2019/08/22<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_8"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Upgrading supported OpenTracing version from 0.31 to 0.33
</li>
<li class="listitem">
<p>Added annotation and meta-annotation matching support for <code class="literal">trace_methods</code>, for example:</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
<code class="literal">public @java.inject.* org.example.*</code> (for annotation)
</li>
<li class="listitem">
<code class="literal">public @@javax.enterprise.context.NormalScope org.example.*</code> (for meta-annotation)
</li>
</ul>
</div>
</li>
<li class="listitem">
The runtime attachment now also works when the <code class="literal">tools.jar</code> or the <code class="literal">jdk.attach</code> module is not available.
This means you don&#8217;t need a full JDK installation - the JRE is sufficient.
This makes the runtime attachment work in more environments such as minimal Docker containers.
Note that the runtime attachment currently does not work for OSGi containers like those used in many application servers such as JBoss and WildFly.
See the <a href="/guide/en/apm/agent/java/master/setup-attach-cli.html" class="ulink" target="_top">documentation</a> for more information.
</li>
<li class="listitem">
Support for Hibernate Search
</li>
</ul>
</div>
<h5><a id="_bug_fixes_8"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
A warning in logs saying APM server is not available when using 1.8 with APM server 6.x.
Due to that, agent 1.8.0 will silently ignore non-string labels, even if used with APM server of versions 6.7.x or 6.8.x that support such.
If APM server version is &lt;6.7 or 7.0+, this should have no effect. Otherwise, upgrade the Java agent to 1.9.0+.
</li>
<li class="listitem">
<code class="literal">ApacheHttpAsyncClientInstrumentation</code> matching increases startup time considerably
</li>
<li class="listitem">
Log correlation feature is active when <code class="literal">active==false</code>
</li>
<li class="listitem">
Tomcat&#8217;s memory leak prevention mechanism is causing a&#8230;&#8203; memory leak. JDBC statement map is leaking in Tomcat if the application that first used it is udeployed/redeployed.
See <a href="https://discuss.elastic.co/t/elastic-apm-agent-jdbchelper-seems-to-use-a-lot-of-memory/195295" class="ulink" target="_top">this related discussion</a>.
</li>
</ul>
</div>
<h4><a id="_breaking_changes_3"></a>Breaking Changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h4>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
The <code class="literal">apm-agent-attach.jar</code> is not executable anymore.
Use <code class="literal">apm-agent-attach-standalone.jar</code> instead.
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.8.0"></a>1.8.0 - 2019/07/30<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_9"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Added support for tracking <a href="/guide/en/kibana/7.3/transactions.html" class="ulink" target="_top">time spent by span type</a>.
Can be disabled by setting <a href="/guide/en/apm/agent/java/current/config-core.html#config-breakdown-metrics" class="ulink" target="_top"><code class="literal">breakdown_metrics</code></a> to <code class="literal">false</code>.
</li>
<li class="listitem">
Added support for <a href="/guide/en/kibana/7.3/agent-configuration.html" class="ulink" target="_top">central configuration</a>.
Can be disabled by setting <a href="/guide/en/apm/agent/java/current/config-core.html#config-central-config" class="ulink" target="_top"><code class="literal">central_config</code></a> to <code class="literal">false</code>.
</li>
<li class="listitem">
Added support for Spring&#8217;s JMS flavor - instrumenting <code class="literal">org.springframework.jms.listener.SessionAwareMessageListener</code>
</li>
<li class="listitem">
Added support to legacy ApacheHttpClient APIs (which adds support to Axis2 configured to use ApacheHttpClient)
</li>
<li class="listitem">
Added support for setting <a href="/guide/en/apm/agent/java/1.x/config-reporter.html#config-server-urls" class="ulink" target="_top"><code class="literal">server_urls</code></a> dynamically via properties file <a href="https://github.com/elastic/apm-agent-java/pull/723" class="ulink" target="_top">#723</a>
</li>
<li class="listitem">
Added <a href="/guide/en/apm/agent/java/current/config-core.html#config-config-file" class="ulink" target="_top"><code class="literal">config_file</code></a> option
</li>
<li class="listitem">
Added option to use <code class="literal">@javax.ws.rs.Path</code> value as transaction name <a href="/guide/en/apm/agent/java/current/config-jax-rs.html#config-use-jaxrs-path-as-transaction-name" class="ulink" target="_top"><code class="literal">use_jaxrs_path_as_transaction_name</code></a>
</li>
<li class="listitem">
Instrument quartz jobs <a href="/guide/en/apm/agent/java/current/supported-technologies-details.html#supported-scheduling-frameworks" class="ulink" target="_top">docs</a>
</li>
<li class="listitem">
SQL parsing improvements <a href="https://github.com/elastic/apm-agent-java/pull/696" class="ulink" target="_top">#696</a>
</li>
<li class="listitem">
Introduce priorities for transaction name <a href="https://github.com/elastic/apm-agent-java/pull/748" class="ulink" target="_top">#748</a>.
Now uses the path as transaction name if <a href="/guide/en/apm/agent/java/current/config-http.html#config-use-path-as-transaction-name" class="ulink" target="_top"><code class="literal">use_path_as_transaction_name</code></a> is set to <code class="literal">true</code>
rather than <code class="literal">ServletClass#doGet</code>.
But if a name can be determined from a high level framework,
like Spring MVC, that takes precedence.
User-supplied names from the API always take precedence over any others.
</li>
<li class="listitem">
Use JSP path name as transaction name as opposed to the generated servlet class name <a href="https://github.com/elastic/apm-agent-java/pull/751" class="ulink" target="_top">#751</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_9"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Some JMS Consumers and Producers are filtered due to class name filtering in instrumentation matching
</li>
<li class="listitem">
Jetty: When no display name is set and context path is "/" transaction service names will now correctly fall back to configured values
</li>
<li class="listitem">
JDBC&#8217;s <code class="literal">executeBatch</code> is not traced
</li>
<li class="listitem">
Drops non-String labels when connected to APM Server &lt; 6.7 to avoid validation errors <a href="https://github.com/elastic/apm-agent-java/pull/687" class="ulink" target="_top">#687</a>
</li>
<li class="listitem">
Parsing container ID in cloud foundry garden <a href="https://github.com/elastic/apm-agent-java/pull/695" class="ulink" target="_top">#695</a>
</li>
<li class="listitem">
Automatic instrumentation should not override manual results <a href="https://github.com/elastic/apm-agent-java/pull/752" class="ulink" target="_top">#752</a>
</li>
</ul>
</div>
<h5><a id="_breaking_changes_4"></a>Breaking changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
The log correlation feature does not add <code class="literal">span.id</code> to the MDC anymore but only <code class="literal">trace.id</code> and <code class="literal">transaction.id</code> <a href="https://github.com/elastic/apm-agent-java/pull/742" class="ulink" target="_top">#742</a>.
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.7.0"></a>1.7.0 - 2019/06/13<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_10"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Added the <code class="literal">trace_methods_duration_threshold</code> config option. When using the <code class="literal">trace_methods</code> config option with wild cards,
this enables considerable reduction of overhead by limiting the number of spans captured and reported
(see more details in config documentation).
NOTE: Using wildcards is still not the recommended approach for the <code class="literal">trace_methods</code> feature.
</li>
<li class="listitem">
Add <code class="literal">Transaction#addCustomContext(String key, String|Number|boolean value)</code> to public API
</li>
<li class="listitem">
Added support for AsyncHttpClient 2.x
</li>
<li class="listitem">
Added <a href="/guide/en/apm/agent/java/current/config-core.html#config-global-labels" class="ulink" target="_top"><code class="literal">global_labels</code></a> configuration option.
This requires APM Server 7.2+.
</li>
<li class="listitem">
Added basic support for JMS- distributed tracing for basic scenarios of <code class="literal">send</code>, <code class="literal">receive</code>, <code class="literal">receiveNoWait</code> and <code class="literal">onMessage</code>.
Both Queues and Topics are supported.
Async <code class="literal">send</code> APIs are not supported in this version.
NOTE: This feature is currently marked as "Incubating" and is disabled by default. In order to enable,
it is required to set the
<a href="/guide/en/apm/agent/java/1.x/config-core.html#config-disable-instrumentations" class="ulink" target="_top"><code class="literal">disable_instrumentations</code></a>
configuration property to an empty string.
</li>
<li class="listitem">
Improved OSGi support: added a configuration option for <code class="literal">bootdelegation</code> packages <a href="https://github.com/elastic/apm-agent-java/pull/641" class="ulink" target="_top">#641</a>
</li>
<li class="listitem">
Better span names for SQL spans. For example, <code class="literal">SELECT FROM user</code> instead of just <code class="literal">SELECT</code> <a href="https://github.com/elastic/apm-agent-java/pull/633" class="ulink" target="_top">#633</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_10"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
ClassCastException related to async instrumentation of Pilotfish Executor causing thread hang (applied workaround)
</li>
<li class="listitem">
NullPointerException when computing Servlet transaction name with null HTTP method name
</li>
<li class="listitem">
FileNotFoundException when trying to find implementation version of jar with encoded URL
</li>
<li class="listitem">
NullPointerException when closing Apache AsyncHttpClient request producer
</li>
<li class="listitem">
Fixes loading of <code class="literal">elasticapm.properties</code> for Spring Boot applications
</li>
<li class="listitem">
Fix startup error on WebLogic 12.2.1.2.0 <a href="https://github.com/elastic/apm-agent-java/pull/649" class="ulink" target="_top">#649</a>
</li>
<li class="listitem">
Disable metrics reporting and APM Server health check when active=false <a href="https://github.com/elastic/apm-agent-java/pull/653" class="ulink" target="_top">#653</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.6.1"></a>1.6.1 - 2019/04/26<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_bug_fixes_11"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fixes transaction name for non-sampled transactions <a href="https://github.com/elastic/apm-agent-java/issues/581" class="ulink" target="_top">#581</a>
</li>
<li class="listitem">
Makes log_file option work again <a href="https://github.com/elastic/apm-agent-java/issues/594" class="ulink" target="_top">#594</a>
</li>
<li class="listitem">
<p>Async context propagation fixes</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fixing some async mechanisms lifecycle issues <a href="https://github.com/elastic/apm-agent-java/issues/605" class="ulink" target="_top">#605</a>
</li>
<li class="listitem">
Fixes exceptions when using WildFly managed executor services <a href="https://github.com/elastic/apm-agent-java/issues/589" class="ulink" target="_top">#589</a>
</li>
<li class="listitem">
Exclude glassfish Executor which does not permit wrapped runnables <a href="https://github.com/elastic/apm-agent-java/issues/596" class="ulink" target="_top">#596</a>
</li>
<li class="listitem">
Exclude DumbExecutor <a href="https://github.com/elastic/apm-agent-java/issues/598" class="ulink" target="_top">#598</a>
</li>
</ul>
</div>
</li>
<li class="listitem">
Fixes Manifest version reading error to support <code class="literal">jar:file</code> protocol <a href="https://github.com/elastic/apm-agent-java/issues/601" class="ulink" target="_top">#601</a>
</li>
<li class="listitem">
Fixes transaction name for non-sampled transactions <a href="https://github.com/elastic/apm-agent-java/issues/597" class="ulink" target="_top">#597</a>
</li>
<li class="listitem">
Fixes potential classloader deadlock by preloading <code class="literal">FileSystems.getDefault()</code> <a href="https://github.com/elastic/apm-agent-java/issues/603" class="ulink" target="_top">#603</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.6.0"></a>1.6.0 - 2019/04/16<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_related_announcements"></a>Related Announcements<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Java APM Agent became part of the Cloud Foundry Java Buildpack as of <a href="https://github.com/cloudfoundry/java-buildpack/releases/tag/v4.19" class="ulink" target="_top">Release v4.19</a>
</li>
</ul>
</div>
<h5><a id="_features_11"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Support Apache HttpAsyncClient - span creation and cross-service trace context propagation
</li>
<li class="listitem">
Added the <code class="literal">jvm.thread.count</code> metric, indicating the number of live threads in the JVM (daemon and non-daemon)
</li>
<li class="listitem">
Added support for WebLogic
</li>
<li class="listitem">
Added support for Spring <code class="literal">@Scheduled</code> and EJB <code class="literal">@Schedule</code> annotations - <a href="https://github.com/elastic/apm-agent-java/pull/569" class="ulink" target="_top">#569</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_12"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Avoid that the agent blocks server shutdown in case the APM Server is not available - <a href="https://github.com/elastic/apm-agent-java/pull/554" class="ulink" target="_top">#554</a>
</li>
<li class="listitem">
Public API annotations improper retention prevents it from being used with Groovy - <a href="https://github.com/elastic/apm-agent-java/pull/567" class="ulink" target="_top">#567</a>
</li>
<li class="listitem">
Eliminate side effects of class loading related to Instrumentation matching mechanism
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.5.0"></a>1.5.0 - 2019/03/26<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_potentially_breaking_changes"></a>Potentially breaking changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
If you didn&#8217;t explicitly set the <a href="/guide/en/apm/agent/java/master/config-core.html#config-service-name" class="ulink" target="_top"><code class="literal">service_name</code></a>
previously and you are dealing with a servlet-based application (including Spring Boot),
your <code class="literal">service_name</code> will change.
See the documentation for <a href="/guide/en/apm/agent/java/master/config-core.html#config-service-name" class="ulink" target="_top"><code class="literal">service_name</code></a>
and the corresponding section in <em>Features</em> for more information.
Note: this requires APM Server 7.0+. If using previous versions, nothing will change.
</li>
</ul>
</div>
<h5><a id="_features_12"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Added property <code class="literal">"allow_path_on_hierarchy"</code> to JAX-RS plugin, to lookup inherited usage of <code class="literal">@path</code>
</li>
<li class="listitem">
Support for number and boolean labels in the public API <a href="https://github.com/elastic/apm-agent-java/pull/497" class="ulink" target="_top">497</a>.
This change also renames <code class="literal">tag</code> to <code class="literal">label</code> on the API level to be compliant with the <a href="https://github.com/elastic/ecs#-base-fields" class="ulink" target="_top">Elastic Common Schema (ECS)</a>.
The <code class="literal">addTag(String, String)</code> method is still supported but deprecated in favor of <code class="literal">addLabel(String, String)</code>.
As of version 7.x of the stack, labels will be stored under <code class="literal">labels</code> in Elasticsearch.
Previously, they were stored under <code class="literal">context.tags</code>.
</li>
<li class="listitem">
Support async queries made by Elasticsearch REST client
</li>
<li class="listitem">
Added <code class="literal">setStartTimestamp(long epochMicros)</code> and <code class="literal">end(long epochMicros)</code> API methods to <code class="literal">Span</code> and <code class="literal">Transaction</code>,
allowing to set custom start and end timestamps.
</li>
<li class="listitem">
Auto-detection of the <code class="literal">service_name</code> based on the <code class="literal">&lt;display-name&gt;</code> element of the <code class="literal">web.xml</code> with a fallback to the servlet context path.
If you are using a spring-based application, the agent will use the setting for <code class="literal">spring.application.name</code> for its <code class="literal">service_name</code>.
See the documentation for <a href="/guide/en/apm/agent/java/master/config-core.html#config-service-name" class="ulink" target="_top"><code class="literal">service_name</code></a>
for more information.
Note: this requires APM Server 7.0+. If using previous versions, nothing will change.
</li>
<li class="listitem">
Previously, enabling <a href="/guide/en/apm/agent/java/master/config-core.html#config-capture-body" class="ulink" target="_top"><code class="literal">capture_body</code></a> could only capture form parameters.
Now it supports all UTF-8 encoded plain-text content types.
The option <a href="/guide/en/apm/agent/java/master/config-http.html#config-capture-body-content-types" class="ulink" target="_top"><code class="literal">capture_body_content_types</code></a>
controls which `Content-Type`s should be captured.
</li>
<li class="listitem">
Support async calls made by OkHttp client (<code class="literal">Call#enqueue</code>)
</li>
<li class="listitem">
<p>Added support for providing config options on agent attach.</p>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
CLI example: <code class="literal">--config server_urls=http://localhost:8200,http://localhost:8201</code>
</li>
<li class="listitem">
API example: <code class="literal">ElasticApmAttacher.attach(Map.of("server_urls", "http://localhost:8200,http://localhost:8201"));</code>
</li>
</ul>
</div>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_13"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Logging integration through MDC is not working properly - <a href="https://github.com/elastic/apm-agent-java/issues/499" class="ulink" target="_top">#499</a>
</li>
<li class="listitem">
ClassCastException with adoptopenjdk/openjdk11-openj9 - <a href="https://github.com/elastic/apm-agent-java/issues/505" class="ulink" target="_top">#505</a>
</li>
<li class="listitem">
Span count limitation is not working properly - reported <a href="https://discuss.elastic.co/t/kibana-apm-not-showing-spans-which-are-visible-in-discover-too-many-spans/171690" class="ulink" target="_top">in our forum</a>
</li>
<li class="listitem">
Java agent causes Exceptions in Alfresco cluster environment due to failure in the instrumentation of Hazelcast `Executor`s - reported <a href="https://discuss.elastic.co/t/cant-run-apm-java-agent-in-alfresco-cluster-environment/172962" class="ulink" target="_top">in our forum</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.4.0"></a>1.4.0 - 2019/02/14<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_13"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Added support for sync calls of OkHttp client
</li>
<li class="listitem">
Added support for context propagation for `java.util.concurrent.ExecutorService`s
</li>
<li class="listitem">
The <code class="literal">trace_methods</code> configuration now allows to omit the method matcher.
Example: <code class="literal">com.example.*</code> traces all classes and methods within the <code class="literal">com.example</code> package and sub-packages.
</li>
<li class="listitem">
Added support for JSF. Tested on WildFly, WebSphere Liberty and Payara with embedded JSF implementation and on Tomcat and Jetty with
MyFaces 2.2 and 2.3
</li>
<li class="listitem">
Introduces a new configuration option <code class="literal">disable_metrics</code> which disables the collection of metrics via a wildcard expression.
</li>
<li class="listitem">
Support for HttpUrlConnection
</li>
<li class="listitem">
Adds <code class="literal">subtype</code> and <code class="literal">action</code> to spans. This replaces former typing mechanism where type, subtype and action were all set through
the type in an hierarchical dotted-syntax. In order to support existing API usages, dotted types are parsed into subtype and action,
however <code class="literal">Span.createSpan</code> and <code class="literal">Span.setType</code> are deprecated starting this version. Instead, type-less spans can be created using the new
<code class="literal">Span.startSpan</code> API and typed spans can be created using the new <code class="literal">Span.startSpan(String type, String subtype, String action)</code> API
</li>
<li class="listitem">
Support for JBoss EAP 6.4, 7.0, 7.1 and 7.2
</li>
<li class="listitem">
Improved startup times
</li>
<li class="listitem">
Support for SOAP (JAX-WS).
SOAP client create spans and propagate context.
Transactions are created for <code class="literal">@WebService</code> classes and <code class="literal">@WebMethod</code> methods.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_14"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fixes a failure in BitBucket when agent deployed <a href="https://github.com/elastic/apm-agent-java/issues/349" class="ulink" target="_top">#349</a>
</li>
<li class="listitem">
Fixes increased CPU consumption <a href="https://github.com/elastic/apm-agent-java/issues/453" class="ulink" target="_top">#453</a> and <a href="https://github.com/elastic/apm-agent-java/issues/443" class="ulink" target="_top">#443</a>
</li>
<li class="listitem">
Fixed some OpenTracing bridge functionalities that were not working when auto-instrumentation is disabled
</li>
<li class="listitem">
Fixed an error occurring when ending an OpenTracing span before deactivating
</li>
<li class="listitem">
Sending proper <code class="literal">null</code> for metrics that have a NaN value
</li>
<li class="listitem">
Fixes JVM crash with Java 7 <a href="https://github.com/elastic/apm-agent-java/issues/458" class="ulink" target="_top">#458</a>
</li>
<li class="listitem">
Fixes an application deployment failure when using EclipseLink and <code class="literal">trace_methods</code> configuration <a href="https://github.com/elastic/apm-agent-java/issues/474" class="ulink" target="_top">#474</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.3.0"></a>1.3.0 - 2019/01/10<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_14"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
The agent now collects system and JVM metrics <a href="https://github.com/elastic/apm-agent-java/pull/360" class="ulink" target="_top">#360</a>
</li>
<li class="listitem">
Add API methods <code class="literal">ElasticApm#startTransactionWithRemoteParent</code> and <code class="literal">Span#injectTraceHeaders</code> to allow for manual context propagation <a href="https://github.com/elastic/apm-agent-java/pull/396" class="ulink" target="_top">#396</a>.
</li>
<li class="listitem">
Added <code class="literal">trace_methods</code> configuration option which lets you define which methods in your project or 3rd party libraries should be traced.
To create spans for all <code class="literal">public</code> methods of classes whose name ends in <code class="literal">Service</code> which are in a sub-package of <code class="literal">org.example.services</code> use this matcher:
<code class="literal">public org.example.services.*.*Service#*</code> <a href="https://github.com/elastic/apm-agent-java/pull/398" class="ulink" target="_top">#398</a>
</li>
<li class="listitem">
Added span for <code class="literal">DispatcherServlet#render</code> <a href="https://github.com/elastic/apm-agent-java/pull/409" class="ulink" target="_top">#409</a>.
</li>
<li class="listitem">
Flush reporter on shutdown to make sure all recorded Spans are sent to the server before the programm exits <a href="https://github.com/elastic/apm-agent-java/pull/397" class="ulink" target="_top">#397</a>
</li>
<li class="listitem">
Adds Kubernetes <a href="https://github.com/elastic/apm-agent-java/issues/383" class="ulink" target="_top">#383</a> and Docker metadata to, enabling correlation with the Kibana Infra UI.
</li>
<li class="listitem">
Improved error handling of the Servlet Async API <a href="https://github.com/elastic/apm-agent-java/issues/399" class="ulink" target="_top">#399</a>
</li>
<li class="listitem">
Support async API’s used with AsyncContext.start <a href="https://github.com/elastic/apm-agent-java/issues/388" class="ulink" target="_top">#388</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_15"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fixing a potential memory leak when there is no connection with APM server
</li>
<li class="listitem">
Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 <a href="https://github.com/elastic/apm-agent-java/pull/401" class="ulink" target="_top">#401</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.2.0"></a>1.2.0 - 2018/12/19<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_15"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Added <code class="literal">capture_headers</code> configuration option.
Set to <code class="literal">false</code> to disable capturing request and response headers.
This will reduce the allocation rate of the agent and can save you network bandwidth and disk space.
</li>
<li class="listitem">
Makes the API methods <code class="literal">addTag</code>, <code class="literal">setName</code>, <code class="literal">setType</code>, <code class="literal">setUser</code> and <code class="literal">setResult</code> fluent, so that calls can be chained.
</li>
</ul>
</div>
<h5><a id="_bug_fixes_16"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Catch all errors thrown within agent injected code
</li>
<li class="listitem">
Enable public APIs and OpenTracing bridge to work properly in OSGi systems, fixes <a href="https://github.com/elastic/apm-agent-java/issues/362" class="ulink" target="_top">this WildFly issue</a>
</li>
<li class="listitem">
Remove module-info.java to enable agent working on early Tomcat 8.5 versions
</li>
<li class="listitem">
Fix <a href="https://github.com/elastic/apm-agent-java/issues/371" class="ulink" target="_top">async Servlet API issue</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.1.0"></a>1.1.0 - 2018/11/28<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_features_16"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Some memory allocation improvements
</li>
<li class="listitem">
Enabling bootdelegation for agent classes in Atlassian OSGI systems
</li>
</ul>
</div>
<h5><a id="_bug_fixes_17"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Update dsl-json which fixes a memory leak.
See <a href="https://github.com/ngs-doo/dsl-json/pull/102" class="ulink" target="_top">ngs-doo/dsl-json#102</a> for details.
</li>
<li class="listitem">
Avoid `VerifyError`s by non instrumenting classes compiled for Java 4 or earlier
</li>
<li class="listitem">
Enable APM Server URL configuration with path (fixes #339)
</li>
<li class="listitem">
Reverse <code class="literal">system.hostname</code> and <code class="literal">system.platform</code> order sent to APM server
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.0.1"></a>1.0.1 - 2018/11/15<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_bug_fixes_18"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 <a href="https://github.com/elastic/apm-agent-java/pull/313" class="ulink" target="_top">#313</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.0.0"></a>1.0.0 - 2018/11/14<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_breaking_changes_5"></a>Breaking changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
Until the time the APM Server 6.5.0 is officially released,
you can test with docker by pulling the APM Server image via
<code class="literal">docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT</code>.
</li>
</ul>
</div>
<h5><a id="_features_17"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Adds <code class="literal">@CaptureTransaction</code> and <code class="literal">@CaptureSpan</code> annotations which let you declaratively add custom transactions and spans.
Note that it is required to configure the <code class="literal">application_packages</code> for this to work.
See the <a href="/guide/en/apm/agent/java/master/public-api.html#api-annotation" class="ulink" target="_top">documentation</a> for more information.
</li>
<li class="listitem">
The public API now supports to activate a span on the current thread.
This makes the span available via <code class="literal">ElasticApm#currentSpan()</code>
Refer to the <a href="/guide/en/apm/agent/java/master/public-api.html#api-span-activate" class="ulink" target="_top">documentation</a> for more details.
</li>
<li class="listitem">
Capturing of Elasticsearch RestClient 5.0.2+ calls.
Currently, the <code class="literal">*Async</code> methods are not supported, only their synchronous counterparts.
</li>
<li class="listitem">
Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
More information can be found in the <a href="/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id" class="ulink" target="_top">documentation</a>.
</li>
<li class="listitem">
Added <code class="literal">Transaction.isSampled()</code> and <code class="literal">Span.isSampled()</code> methods to the public API
</li>
<li class="listitem">
Added <code class="literal">Transaction#setResult</code> to the public API <a href="https://github.com/elastic/apm-agent-java/pull/293" class="ulink" target="_top">#293</a>
</li>
</ul>
</div>
<h5><a id="_bug_fixes_19"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fix for situations where status code is reported as <code class="literal">200</code>, even though it actually was <code class="literal">500</code> <a href="https://github.com/elastic/apm-agent-java/pull/225" class="ulink" target="_top">#225</a>
</li>
<li class="listitem">
Capturing the username now properly works when using Spring security <a href="https://github.com/elastic/apm-agent-java/pull/183" class="ulink" target="_top">#183</a>
</li>
</ul>
</div>
</div>

<div class="section">
<div class="titlepage"><div><div>
<h3 class="title"><a id="release-notes-1.0.0.rc1"></a>1.0.0.RC1 - 2018/11/06<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h3>
</div></div></div>
<h5><a id="_breaking_changes_6"></a>Breaking changes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
Until the time the APM Server 6.5.0 is officially released,
you can test with docker by pulling the APM Server image via
<code class="literal">docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT</code>.
</li>
<li class="listitem">
Wildcard patterns are case insensitive by default. Prepend <code class="literal">(?-i)</code> to make the matching case sensitive.
</li>
</ul>
</div>
<h5><a id="_features_18"></a>Features<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Support for Distributed Tracing
</li>
<li class="listitem">
Adds <code class="literal">@CaptureTransaction</code> and <code class="literal">@CaptureSpan</code> annotations which let you declaratively add custom transactions and spans.
Note that it is required to configure the <code class="literal">application_packages</code> for this to work.
See the <a href="/guide/en/apm/agent/java/master/public-api.html#api-annotation" class="ulink" target="_top">documentation</a> for more information.
</li>
<li class="listitem">
The public API now supports to activate a span on the current thread.
This makes the span available via <code class="literal">ElasticApm#currentSpan()</code>
Refer to the <a href="/guide/en/apm/agent/java/master/public-api.html#api-span-activate" class="ulink" target="_top">documentation</a> for more details.
</li>
<li class="listitem">
Capturing of Elasticsearch RestClient 5.0.2+ calls.
Currently, the <code class="literal">*Async</code> methods are not supported, only their synchronous counterparts.
</li>
<li class="listitem">
Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
More information can be found in the <a href="/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id" class="ulink" target="_top">documentation</a>.
</li>
<li class="listitem">
Microsecond accurate timestamps <a href="https://github.com/elastic/apm-agent-java/pull/261" class="ulink" target="_top">#261</a>
</li>
<li class="listitem">
Support for JAX-RS annotations.
Transactions are named based on your resources (<code class="literal">ResourceClass#resourceMethod</code>).
</li>
</ul>
</div>
<h5><a id="_bug_fixes_20"></a>Bug Fixes<a class="edit_me" rel="nofollow" title="Edit this page on GitHub" href="https://github.com/elastic/apm-agent-java/edit/1.x/CHANGELOG.asciidoc">edit</a></h5>
<div class="ulist itemizedlist">
<ul class="itemizedlist">
<li class="listitem">
Fix for situations where status code is reported as <code class="literal">200</code>, even though it actually was <code class="literal">500</code> <a href="https://github.com/elastic/apm-agent-java/pull/225" class="ulink" target="_top">#225</a>
</li>
</ul>
</div>
</div>

</div>
<div class="navfooter">
<span class="prev">
<a href="_unreleased.html">« Unreleased</a>
</span>
<span class="next">
<a href="release-notes-0.8.x.html">Java Agent version 0.8.x »</a>
</span>
</div>
</div>

                  <!-- end body -->
                </div>
                <div class="col-xs-12 col-sm-4 col-md-4" id="right_col">
                  <div id="rtpcontainer" style="display: block;">
                    <div class="mktg-promo">
                      <h3>Most Popular</h3>
                      <ul class="icons">
                        <li class="icon-elasticsearch-white"><a href="https://www.elastic.co/webinars/getting-started-elasticsearch?baymax=default&elektra=docs&storm=top-video">Get Started with Elasticsearch: Video</a></li>
                        <li class="icon-kibana-white"><a href="https://www.elastic.co/webinars/getting-started-kibana?baymax=default&elektra=docs&storm=top-video">Intro to Kibana: Video</a></li>
                        <li class="icon-logstash-white"><a href="https://www.elastic.co/webinars/introduction-elk-stack?baymax=default&elektra=docs&storm=top-video">ELK for Logs & Metrics: Video</a></li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>

        </div>


<div id='elastic-footer'></div>
<script src='https://www.elastic.co/elastic-footer.js'></script>
<!-- Footer Section end-->

      </section>
    </div>

<script type="text/javascript">
	var suggestionsUrl = "https://search.elastic.co/suggest";
	var localeUrl = '{"relative_url_prefix":"/","code":"en-us","display_code":"en-us","url":"/guide_template"}';
</script>
<script src="/static/js/swiftype_app_search.umd.min.js"></script>
<script src="/guide/static/jquery.js"></script>
<script type="text/javascript" src="/guide/static/docs.js"></script>
<script type="text/javascript">
  window.initial_state = {}</script>
  </body>
</html>
"""
