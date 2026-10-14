import copy, os, re, subprocess, json
import kitsune_art as kit2
from kitsune_art import *
import kitsune_buff as kitbuff

import tempfile
RES=os.path.join(kit2.ROOT,'app/src/main/res')
SCR=tempfile.mkdtemp()

# --- occluder for the icon geometry ---
_prev=kit2.hidden
def hidden_icon(x,y,occ):
    if 'iconhead' in occ and ((x-50)/23.5)**2+((y-54)/22)**2<1: return True
    return _prev(x,y,occ)
kit2.hidden=hidden_icon

def icon_shapes(form='default'):
    pal=PAL[form]
    shapes=[]
    for ang in (-62,62,-40,40,-18,18,0):
        for s in plume(ang, 44 if abs(ang)<50 else 36, 9, (50,74)):
            if s.occ: s.occ=('iconhead',)
            shapes.append(s)
    f=lambda x,y:(x*0.64+18, y*0.64+15)
    K=0.64
    for s in kit2.face():
        if s.occ: s.occ=('iconhead',)
        if s.kind=='fill' and s.role in ('iris','irislow','pupil','white','blush'):
            m=re.match(r'M(-?[\d.]+),(-?[\d.]+) a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 Z',s.path)
            x,y,rx,ry=float(m[1]),float(m[2]),float(m[3]),float(m[4]); nx,ny=f(x,y)
            s.path=f"M{num(nx)},{num(ny)} a{num(rx*K)},{num(ry*K)} 0 1,0 {num(2*rx*K)},0 a{num(rx*K)},{num(ry*K)} 0 1,0 {num(-2*rx*K)},0 Z"
        else:
            s.path=xform(s.path,f)
            if s.kind=='stroke': s.w*=K
        shapes.append(s)
    return shapes,pal

def kit2_main():
    for form in PAL:
        sh,pal=kit2.kitsune(form)
        kit2.write('kitsune_'+form, sh, pal)
        kit2.write_line_pair('kitsune_'+form+'_line', sh, pal)
    import copy
    sh=kitbuff.buff()
    kit2.write('kitsune_buff', sh, kitbuff.BPAL)
    kit2.write_line_pair('kitsune_buff_line', copy.deepcopy(sh), kitbuff.BPAL)

def write_icon(name, line):
    shapes,pal=icon_shapes('default')
    lines=render(shapes,pal,line=line)
    head=('<?xml version="1.0" encoding="utf-8"?>\n<!-- Kitsune launcher mark: head and nine-tail fan, kept inside the adaptive-icon safe zone. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
          '    <group android:translateX="6.5" android:translateY="6.5" android:scaleX="0.95" android:scaleY="0.95">\n')
    open(f'{RES}/drawable/{name}.xml','w').write(head+'\n'.join('    '+l for l in lines)+'\n    </group>\n</vector>\n')

def svg_of(shapes,pal,line=False,size=512,bg=None,shape=None, inner=(0,0,100,100), scale=0.80):
    """SVG text for PNG export. bg: color; shape: 'round'|'square'|'rounded'"""
    body=[]
    for l in render(shapes,pal,line=line):
        m=re.search(r'android:pathData="([^"]+)"',l); d=m[1]
        def g(k):
            mm=re.search(k+r'="([^"]+)"',l); return mm[1] if mm else None
        def cssc(c):
            if not c: return 'none'
            if c.startswith('#') and len(c)==9: return f'rgba({int(c[3:5],16)},{int(c[5:7],16)},{int(c[7:9],16)},{int(c[1:3],16)/255:.2f})'
            return c
        at=f'd="{d}" fill="{cssc(g("android:fillColor"))}"'
        if g('android:strokeColor'): at+=f' stroke="{cssc(g("android:strokeColor"))}" stroke-width="{g("android:strokeWidth")}" stroke-linecap="round" stroke-linejoin="round"'
        if g('android:fillAlpha'): at+=f' fill-opacity="{g("android:fillAlpha")}"'
        body.append(f'<path {at}/>')
    x,y,w,h=inner
    clip=''; bgsvg=''
    if bg:
        if shape=='round': bgsvg=f'<circle cx="50" cy="50" r="50" fill="{bg}"/>'
        elif shape=='rounded': bgsvg=f'<rect width="100" height="100" rx="22" fill="{bg}"/>'
        else: bgsvg=f'<rect width="100" height="100" fill="{bg}"/>'
    # place art (100x100 coordinates) into the square with a safe margin
    off=(1-scale)*50
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 100 100">{bgsvg}'
            f'<g transform="translate({off},{off}) scale({scale})">{"".join(body)}</g></svg>')

def export_pngs():
    jobs=[]
    shapes,pal=icon_shapes('default')
    def add(path,size,shape,bg='#102C3A',sh=None,pal_=None,line=False,scale=1.15):
        svg=svg_of(sh or shapes,pal_ or pal,line=line,size=size,bg=bg,shape=shape,scale=scale)
        f=f'{SCR}/png_{len(jobs)}.svg'; open(f,'w').write(svg); jobs.append({'svg':f,'png':path,'size':size})
    dens={'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
    for d,sz in dens.items():
        add(f'{RES}/mipmap-{d}/ic_launcher.png',sz,'rounded')
        add(f'{RES}/mipmap-{d}/ic_launcher_round.png',sz,'round')
    add(os.path.join(kit2.ROOT,'app/src/main/ic_launcher-playstore.png'),512,'square')
    # widget previews: Kitsune forms on a soft background
    for nm,form in (('ic_appwidget_shelf','book'),('ic_appwidget_recent','video')):
        sh,p=kit2.kitsune(form)
        add(f'{RES}/drawable-nodpi/{nm}.png',192,'rounded',bg='#F4EFE8',sh=sh,pal_=p,scale=0.86)
    json.dump(jobs,open(f'{SCR}/jobs.json','w'))
    open(f'{SCR}/svg2png.js','w').write('''
const { chromium } = require(process.argv[2]);
const jobs=require(process.argv[3]); const fs=require('fs');
(async()=>{ const b=await chromium.launch(); const p=await b.newPage();
 for(const j of jobs){ await p.setViewportSize({width:j.size,height:j.size});
  await p.setContent('<body style="margin:0;background:transparent">'+fs.readFileSync(j.svg,'utf8')+'</body>');
  await p.screenshot({path:j.png,omitBackground:true,clip:{x:0,y:0,width:j.size,height:j.size}}); }
 await b.close(); })();''')
    subprocess.check_call(['node',f'{SCR}/svg2png.js',os.environ.get('PLAYWRIGHT_MODULE') or subprocess.check_output(['npm','root','-g']).decode().strip()+'/playwright',f'{SCR}/jobs.json'])

if __name__=='__main__':
    kit2_main()
    write_icon('monogatari_icon',False)
    write_icon('monogatari_icon_mono',True)
    export_pngs()
    for d in ('mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi'):
        for n in ('ic_launcher','ic_launcher_round','ic_launcher_foreground'):
            f=f'{RES}/mipmap-{d}/{n}.webp'
            if os.path.exists(f): os.remove(f)
    print('icons ok')
