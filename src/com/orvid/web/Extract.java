package com.orvid.web;

public final class Extract {

    private Extract() {
    }

    public static String cards(String linkPattern) {
        return "(function(re){var out=[],seen={};var rx=new RegExp(re,'i');var as=document.querySelectorAll('a[href]');"
                + "for(var i=0;i<as.length&&out.length<60;i++){var a=as[i];var h=a.href;if(!rx.test(h)||seen[h])continue;"
                + "var card=a;for(var k=0;k<5&&card.parentElement;k++){if(card.querySelector('img'))break;card=card.parentElement;}"
                + "var img=card.querySelector('img');if(!img)continue;"
                + "var t=a.getAttribute('title')||img.getAttribute('alt')||'';"
                + "if(!t||t.length<3){var tt=card.querySelector('[title]');if(tt)t=tt.getAttribute('title');}"
                + "if(!t||t.length<3)t=(a.textContent||'').trim();"
                + "var src=img.getAttribute('data-src')||img.getAttribute('data-original')||img.getAttribute('data-thumb')||img.currentSrc||img.src||'';"
                + "if(src.indexOf('data:')==0)src=img.getAttribute('data-srcset')||'';"
                + "src=src.split(' ')[0];try{src=new URL(src,location.href).href;}catch(e){src='';}"
                + "var m=(card.textContent||'').match(/(\\d{1,2}:\\d{2}(?::\\d{2})?)/);"
                + "seen[h]=1;out.push({u:h,t:(t||'').trim().substring(0,200),i:src,d:m?m[1]:''});}"
                + "return JSON.stringify(out);})(" + quote(linkPattern) + ")";
    }

    static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
