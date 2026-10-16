package com.orvid.web;

public final class Focus {

    private Focus() {
    }

    public static final String ON = "(function(){if(window.__orvidOff)return 'off';if(window.__orvidFocus&&document.contains(window.__orvidFocus))return 'done';"
            + "var site=location.hostname.split('.').slice(-2).join('.');var cand=[];"
            + "[].forEach.call(document.querySelectorAll('video'),function(v){cand.push([v,2]);});"
            + "[].forEach.call(document.querySelectorAll('iframe'),function(f){var h='';try{h=new URL(f.src,location.href).hostname;}catch(e){}"
            + "if(h.split('.').slice(-2).join('.')===site||/\\/(embed|player)\\//i.test(f.src||''))cand.push([f,1]);});"
            + "[].forEach.call(document.querySelectorAll('[id*=player],[class*=player],[id*=Player],[class*=Player],[class*=jwplayer],[class*=video-js],[class*=plyr],[class*=flowplayer],[class*=fp-player],[id*=video-container],[class*=video-container]'),function(e){cand.push([e,0]);});"
            + "var best=null,score=0;cand.forEach(function(p){var e=p[0];var r=e.getBoundingClientRect();var a=r.width*r.height;if(a<15000)return;"
            + "var ratio=r.width/Math.max(1,r.height);if(ratio<1.1||ratio>2.6)return;var s=a*(1+p[1]*0.15);if(s>score){score=s;best=e;}});"
            + "if(!best)return 'none';var c=best;"
            + "for(var i=0;i<3&&c.parentElement&&c.parentElement!==document.body;i++){var p=c.parentElement.getBoundingClientRect(),r=c.getBoundingClientRect();"
            + "if(p.width*p.height>r.width*r.height*1.4)break;c=c.parentElement;}"
            + "window.__orvidFocus=c;var st=document.createElement('style');st.id='__orvid_style';"
            + "st.textContent='html,body{overflow:hidden!important;background:#000!important}'"
            + "+'.__orvid_focus{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;z-index:2147483647!important;background:#000!important;transform:none!important}'"
            + "+'.__orvid_focus video,.__orvid_focus iframe{width:100%!important;height:100%!important;object-fit:contain!important}';"
            + "(document.head||document.documentElement).appendChild(st);c.classList.add('__orvid_focus');"
            + "var n=c;while(n&&n!==document.body){var q=n.parentElement;if(!q)break;[].forEach.call(q.children,function(s){"
            + "if(s!==n&&s.tagName!=='SCRIPT'&&s.tagName!=='STYLE'){s.style.setProperty('visibility','hidden','important');s.setAttribute('data-orvid-hidden','1');}});n=q;}"
            + "window.scrollTo(0,0);return 'focused:'+c.tagName+'.'+String(c.className).substring(0,30);})()";

    public static final String OFF = "(function(){var c=window.__orvidFocus;if(c)c.classList.remove('__orvid_focus');"
            + "var st=document.getElementById('__orvid_style');if(st)st.remove();"
            + "[].forEach.call(document.querySelectorAll('[data-orvid-hidden]'),function(e){e.style.removeProperty('visibility');e.removeAttribute('data-orvid-hidden');});"
            + "window.__orvidFocus=null;window.__orvidOff=true;return 'off';})()";

    public static final String RESUME = "(function(){window.__orvidOff=false;return 'on';})()";
}
