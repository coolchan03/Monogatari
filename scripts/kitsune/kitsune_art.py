"""Kitsune art generator: writes the mascot VectorDrawables (colour and line-art sets). Run build_kitsune.py."""
import math, os, re
ROOT=os.path.abspath(os.path.join(os.path.dirname(__file__),'..','..'))
OUT=os.path.join(ROOT,'app/src/main/res/drawable')
CREAM='#FFF3E4'; DARK='#2A1838'; RED='#E5393B'; BLUSH='#FF8A9A'

# ---------- geometry helpers ----------
def num(n): 
    s=('%.1f'%n).rstrip('0').rstrip('.'); return s if s!='-0' else '0'
def xform(path, f):
    """apply f(x,y)->(x,y) to an absolute M/L/C/Q/Z path"""
    toks=re.findall(r'[MLCQZ]|-?\d+\.?\d*',path); out=[]; i=0; pend=[]
    while i<len(toks):
        t=toks[i]
        if t in 'MLCQZ': out.append(t); i+=1
        else:
            x=float(toks[i]); y=float(toks[i+1]); nx,ny=f(x,y); out.append(num(nx)+','+num(ny)); i+=2
    return ' '.join(out)
def mirror(path): return xform(path, lambda x,y:(100-x,y))
def rot(path, ang, cx, cy):
    a=math.radians(ang); c,s=math.cos(a),math.sin(a)
    return xform(path, lambda x,y:(cx+(x-cx)*c-(y-cy)*s, cy+(x-cx)*s+(y-cy)*c))
def shift(path, dx, dy): return xform(path, lambda x,y:(x+dx,y+dy))
def circle(cx,cy,r): return f"M{num(cx-r)},{num(cy)} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0 Z"
def ellipse(cx,cy,rx,ry): return f"M{num(cx-rx)},{num(cy)} a{rx},{ry} 0 1,0 {2*rx},0 a{rx},{ry} 0 1,0 {-2*rx},0 Z"

# A shape: (role, path, kind) kind in fill|stroke ; stroke has width
class S:
    def __init__(s, role, path, kind='fill', w=1.4, alpha=None, occ=()): s.role=role; s.path=path; s.kind=kind; s.w=w; s.alpha=alpha; s.occ=occ

def both(role, path, w=1.4, alpha=None, occ=()):  # mirrored pair
    return [S(role,path,'fill',w,alpha,occ), S(role,mirror(path),'fill',w,alpha,occ)]
def both_ellipse(role,cx,cy,rx,ry,alpha=None):
    return [S(role,ellipse(cx,cy,rx,ry),'fill',1.4,alpha), S(role,ellipse(100-cx,cy,rx,ry),'fill',1.4,alpha)]

def sample(path, n=14):
    toks=re.findall(r'[MLCQZ]|-?\d+\.?\d*',path); i=0; pts=[]; cur=(0,0); start=(0,0); cmd=None
    while i<len(toks):
        t=toks[i]
        if t in 'MLCQZ':
            cmd=t; i+=1
            if t=='Z': pts.append(start); continue
            continue
        if cmd=='M': cur=(float(toks[i]),float(toks[i+1])); start=cur; pts.append(cur); i+=2; cmd='L'
        elif cmd=='L': cur=(float(toks[i]),float(toks[i+1])); pts.append(cur); i+=2
        elif cmd=='C':
            p=[(float(toks[i+k]),float(toks[i+k+1])) for k in (0,2,4)]; i+=6
            for j in range(1,n+1):
                u=j/n; a=(1-u)**3; b=3*u*(1-u)**2; c=3*u*u*(1-u); d=u**3
                pts.append((a*cur[0]+b*p[0][0]+c*p[1][0]+d*p[2][0], a*cur[1]+b*p[0][1]+c*p[1][1]+d*p[2][1]))
            cur=p[2]
        elif cmd=='Q':
            p=[(float(toks[i+k]),float(toks[i+k+1])) for k in (0,2)]; i+=4
            for j in range(1,n+1):
                u=j/n; pts.append(((1-u)**2*cur[0]+2*u*(1-u)*p[0][0]+u*u*p[1][0], (1-u)**2*cur[1]+2*u*(1-u)*p[0][1]+u*u*p[1][1]))
            cur=p[1]
    return pts

def hidden(x,y,occ):
    if 'head' in occ and ((x-50)/28.5)**2+((y-55)/26.5)**2<1: return True
    if 'robe' in occ and y>74+((x-50)/42)**2*10: return True
    return False

def visible_strokes(path, occ):
    runs=[]; run=[]
    for (x,y) in sample(path):
        if hidden(x,y,occ):
            if len(run)>1: runs.append(run)
            run=[]
        else: run.append((x,y))
    if len(run)>1: runs.append(run)
    return ' '.join('M'+' L'.join(f'{num(x)},{num(y)}' for x,y in r) for r in runs)
def both_stroke(role, path, w=1.4):
    return [S(role,path,'stroke',w), S(role,mirror(path),'stroke',w)]

def render(shapes, pal, line=False):
    out=[]
    for s in shapes:
        if line:
            if s.role in ('bg','blush','shade','glow','innerear','tailtip','cheekcream','irislow','tail2deco','drop'): continue
            if s.occ:
                d=visible_strokes(s.path,s.occ)
                if d: out.append(f'    <path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="#FFFFFF" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" />')
                continue
            col='#FFFFFF'
            if s.kind=='stroke': a=f'android:strokeColor="{col}" android:strokeWidth="{s.w}" android:strokeLineCap="round" android:strokeLineJoin="round"'; f='android:fillColor="#00000000"'
            elif s.role in ('pupil','nose','iris'): a=''; f=f'android:fillColor="{col}"'
            else: a=f'android:strokeColor="{col}" android:strokeWidth="{max(1.1,min(s.w,1.8))}" android:strokeLineJoin="round" android:strokeLineCap="round"'; f='android:fillColor="#00000000"'
            out.append(f'    <path android:pathData="{s.path}" {f} {a} />')
        else:
            c=pal.get(s.role,'#FF00FF')
            if s.kind=='stroke': at=f'android:fillColor="#00000000" android:strokeColor="{c}" android:strokeWidth="{s.w}" android:strokeLineCap="round" android:strokeLineJoin="round"'
            else:
                at=f'android:fillColor="{c}"'
                if s.alpha is not None: at+=f' android:fillAlpha="{s.alpha}"'
                if s.role in ('fur','robe','skin','hair','tail','tail2','pants'): at+=f' android:strokeColor="{pal.get("line",DARK)}" android:strokeWidth="0.9" android:strokeLineJoin="round"'
            out.append(f'    <path android:pathData="{s.path}" {at} />')
    return out

def write(name, shapes, pal, line=False, tint=None, vp=100, out=None):
    head=('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="160dp"\n    android:height="160dp"\n'+(f'    android:tint="{tint}"\n' if tint else '')+
          f'    android:viewportWidth="{vp}"\n    android:viewportHeight="{vp}">\n')
    open(os.path.join(out or OUT,name+'.xml'),'w').write(head+'\n'.join(render(shapes,pal,line))+'\n</vector>\n')

# ---------- Kitsune: nine tails, bust, anime face ----------
def plume(ang, length=46, width=10.5, base=(50,92)):
    bx,by=base; L=length; W=width
    body=f"M{bx-W},{by} C{bx-W*2.1},{by-L*.34} {bx-W*1.9},{by-L*.82} {bx},{by-L} C{bx+W*1.9},{by-L*.82} {bx+W*2.1},{by-L*.34} {bx+W},{by} Z"
    tip =f"M{bx-W*1.55},{by-L*.82} C{bx-W*1.3},{by-L*.88} {bx-W*.5},{by-L*.96} {bx},{by-L} C{bx+W*.5},{by-L*.96} {bx+W*1.3},{by-L*.88} {bx+W*1.55},{by-L*.82} C{bx+W*.7},{by-L*.82} {bx-W*.7},{by-L*.82} {bx-W*1.55},{by-L*.82} Z"
    vein=f"M{bx},{by-4} C{bx-1},{by-L*.4} {bx+1},{by-L*.7} {bx},{by-L*.9}"
    return [S('tail' if (ang//28)%2==0 else 'tail2', rot(body,ang,bx,by), occ=('head','robe')), S('tailtip', rot(tip,ang,bx,by)), S('shade', rot(vein,ang,bx,by),'stroke',0.8)]

def tails():
    L=[]
    for ang in (-84,84,-56,56,-28,28,0):
        L+=plume(ang, 56 if abs(ang)<60 else 46)
    return L

def face():
    L=[]
    # ears (big, curved) with fluffy inner tufts
    L+=both('fur',"M19,48 C9,32 8,16 13,2 C27,8 39,17 45,31 Z",occ=('head',))
    L+=both('innerear',"M21,40 C15,29 15,18 18,9 C27,14 34,21 38,30 Z")
    L+=both('cream',"M21,38 L28,31 L26,40 L33,34 L31,42 L37,37 L34,31 L26,26 Z")
    # cheek fluff tufts (spiky), then head
    L+=both('fur',"M17,56 L4,60 L15,64 L7,73 L19,72 L16,82 L29,78 L24,64 Z",occ=('head',))
    L.append(S('fur',"M50,94 C29,92 13,76 13,54 C13,37 29,26 50,26 C71,26 87,37 87,54 C87,76 71,92 50,94 Z"))
    L+=both('cheekcream',"M13,56 C23,60 31,80 50,94 C30,96 15,84 13,56 Z")
    L.append(S('cream',"M33,70 C40,60 60,60 67,70 C65,86 35,86 33,70 Z"))
    # forehead tuft and mark
    L.append(S('fur',"M50,27 C44,17 50,9 59,3 C57,13 60,21 56,29 Z"))
    L.append(S('red',"M50,31 L53.5,36 L50,41 L46.5,36 Z"))
    # eyes
    for sx in (1,-1):
        def X(x): return x if sx==1 else 100-x
        L.append(S('dark',f"M{X(22)},52 C{X(22)},41 {X(44)},41 {X(44)},52 C{X(44)},65 {X(22)},65 {X(22)},52 Z"))
        L.append(S('iris',ellipse(X(33),53,7.2,9.4)))
        L.append(S('irislow',ellipse(X(33),58.5,5.6,4.2),alpha=0.55))
        L.append(S('pupil',ellipse(X(33),53.5,3,5.2)))
        L.append(S('white',circle(X(29.2),49,3.1)))
        L.append(S('white',circle(X(37),58.5,1.5)))
        L.append(S('stroke',f"M{X(19)},51 C{X(21)},41 {X(38)},37.5 {X(44)},46",'stroke',2.8))
        L.append(S('stroke',f"M{X(19.5)},49.5 L{X(15)},45",'stroke',2.4))
        L.append(S('stroke',f"M{X(24)},37 C{X(30)},33.5 {X(37)},33.5 {X(41)},36",'stroke',1.4))
    # nose, mouth, blush, whisker marks
    L.append(S('nose',"M47,69.5 C48,68 52,68 53,69.5 C52.5,72 51,73 50,73.5 C49,73 47.5,72 47,69.5 Z"))
    L.append(S('stroke',"M50,73.5 L50,76 M50,76 C48,79 44,79 42,76.5 M50,76 C52,79 56,79 58,76.5",'stroke',1.4))
    L+=both_ellipse('blush',25,67,4.2,2.6,alpha=0.6)
    L+=both_stroke('red',"M17,58 C22,60 27,62 32,62.5 M17,65 C21,66 25,67 29,67.5",2.2)
    return L

def robe(accent, motif):
    L=[]
    L.append(S('robe',"M8,100 C9,84 24,74 50,74 C76,74 91,84 92,100 Z"))
    L+=both('shade',"M8,100 C9,90 14,83 22,79 L28,100 Z",alpha=0.35)
    L.append(S('cream',"M50,96 L36,74 L42,72 L50,84 L58,72 L64,74 Z"))
    L.append(S('stroke',"M36,74 L50,96 L64,74",'stroke',1.6))
    L.append(S('accent',"M30,100 L50,90 L70,100 L66,100 L50,93 L34,100 Z"))
    L+=motif
    return L

def accessories(form):
    L=[]
    if form=='manga':
        L.append(S('red',"M84,2 L97,2 L97,26 L90.5,20 L84,26 Z"))
        L.append(S('white',"M3,5 C3,2 6,1 9,1 L31,1 C34,1 37,2 37,5 L37,14 C37,17 34,18 31,18 L17,18 L8,25 L10,18 C6,18 3,17 3,14 Z"))
        L.append(S('stroke',"M3,5 C3,2 6,1 9,1 L31,1 C34,1 37,2 37,5 L37,14 C37,17 34,18 31,18 L17,18 L8,25 L10,18 C6,18 3,17 3,14 Z",'stroke',1.2))
        L.append(S('stroke',"M20,5 L20,11",'stroke',2)); L.append(S('stroke',circle(20,14,0.6),'stroke',1.6))
    if form=='video':
        L.append(S('glow',circle(50,52,48),alpha=0.28))
        L.append(S('accent',"M42,0 L42,15 L56,7.5 Z"))
    if form=='audio':
        L.append(S('stroke',"M90,3 L90,16 M90,3 L97,5 M86,16 a3.6,3.6 0 1,0 7.2,0 a3.6,3.6 0 1,0 -7.2,0",'stroke',2))
    if form=='news':
        L.append(S('white',"M22,88 L78,88 L78,99 L22,99 Z")); L.append(S('stroke',"M22,88 L78,88 L78,99 L22,99 Z",'stroke',1.1))
        L.append(S('stroke',"M26,91.5 L46,91.5 M26,95.5 L46,95.5 M54,91.5 L74,91.5 M54,95.5 L74,95.5",'stroke',1.2))
    if form=='book':
        L.append(S('white',"M24,90 L50,85 L76,90 L76,100 L50,95 L24,100 Z")); L.append(S('stroke',"M24,90 L50,85 L76,90 L76,100 L50,95 L24,100 Z M50,85 L50,95",'stroke',1.1))
    return L

def hitodama(accent_role='accent'):
    def fl(x,y,s): return S(accent_role,f"M{x},{y} C{x+6*s},{y+6*s} {x+6.5*s},{y+13*s} {x},{y+15*s} C{x-6.5*s},{y+13*s} {x-6*s},{y+6*s} {x},{y} Z")
    return [fl(10,22,0.9), fl(90,20,0.7), fl(50,0,0.9)]

def head_extras(form):
    L=[]
    if form=='book':
        for cx in (33,67):
            L.append(S('stroke',circle(cx,53,12.3),'stroke',2.2))
        L.append(S('stroke',"M45.3,53 L54.7,53",'stroke',2.2)); L.append(S('stroke',"M20.7,50 L12,46 M79.3,50 L88,46",'stroke',2))
    if form=='audio':
        L.append(S('stroke',"M11,54 C8,12 92,12 89,54",'stroke',5))
        L.append(S('headphone',"M3,46 L16,46 L16,70 L3,70 Z")); L.append(S('headphone',"M84,46 L97,46 L97,70 L84,70 Z"))
    return L

PAL = {
 'default': dict(fur='#2BB5A6', tail='#2BB5A6', tail2='#5BD2C4', robe='#1D4E6B', accent='#9BE7F0', iris='#0E8A82', irislow='#7FF0E0', glow='#9BE7F0', headphone='#37474F'),
 'manga':   dict(fur='#2BB5A6', tail='#2BB5A6', tail2='#5BD2C4', robe='#1F4B66', accent='#FF6F61', iris='#0E8A82', irislow='#7FF0E0', glow='#9BE7F0', headphone='#37474F'),
 'book':    dict(fur='#F2A33A', tail='#F2A33A', tail2='#FFC875', robe='#6B3F2A', accent='#FFD479', iris='#8A4B1E', irislow='#FFD28A', glow='#FFD479', headphone='#37474F'),
 'video':   dict(fur='#8E63D9', tail='#8E63D9', tail2='#B592F2', robe='#2B1B5A', accent='#FFE45C', iris='#4B2AA8', irislow='#C7AEFF', glow='#B592F2', headphone='#37474F'),
 'audio':   dict(fur='#FF7A59', tail='#FF7A59', tail2='#FFA58E', robe='#5A1F2E', accent='#FFD1C4', iris='#B8321B', irislow='#FFC2B3', glow='#FFB199', headphone='#37474F'),
 'news':    dict(fur='#4C9BF0', tail='#4C9BF0', tail2='#86BBF7', robe='#1B2F5A', accent='#FFD65C', iris='#1B55B5', irislow='#A9CCFF', glow='#86BBF7', headphone='#37474F'),
}
for p in PAL.values():
    p.update(cheekcream=CREAM, cream=CREAM, dark=DARK, red=RED, blush=BLUSH, white='#FFFFFF', pupil=DARK, nose=DARK, tailtip=CREAM, shade='#33000000', stroke=DARK, innerear='#3A2347', line=DARK)

def kitsune(form):
    pal=PAL[form]
    shapes=[S('bg',circle(50,50,49),alpha=0.0)] if False else []
    if form=='video': shapes.append(S('glow',circle(50,50,49),alpha=0.28))
    shapes+=tails()
    shapes+=robe(pal['accent'],[])
    # head group is applied via transform: pre-transform the face coordinates instead of <group>
    face_shapes=face()+head_extras(form)
    f=lambda x,y:(x*0.76+12, y*0.76+9)
    for s in face_shapes:
        if s.kind=='fill' and s.path.count('a')>0 and s.role in ('iris','irislow','pupil','white','blush'):
            # circles/ellipses use relative arcs: scale numbers directly
            m=re.match(r'M(-?[\d.]+),(-?[\d.]+) a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 Z',s.path)
            if m:
                x,y,rx,ry=float(m[1]),float(m[2]),float(m[3]),float(m[4]); nx,ny=f(x,y); s.path=f"M{num(nx)},{num(ny)} a{num(rx*.76)},{num(ry*.76)} 0 1,0 {num(2*rx*.76)},0 a{num(rx*.76)},{num(ry*.76)} 0 1,0 {num(-2*rx*.76)},0 Z"; s.w=s.w*.76; shapes.append(s); continue
        if s.kind=='stroke' and 'a' in s.path.replace('M','').replace('C','').replace('L','') and re.search(r' a',s.path):
            s.path=re.sub(r'a(\d)',r'a\1',s.path)  # arcs in strokes (glasses) -> handled below
            m=re.match(r'M(-?[\d.]+),(-?[\d.]+) a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 a([\d.]+),([\d.]+) 0 1,0 (-?[\d.]+),0 Z',s.path)
            if m:
                x,y,rx=float(m[1]),float(m[2]),float(m[3]); nx,ny=f(x,y); r=rx*.76
                s.path=f"M{num(nx)},{num(ny)} a{num(r)},{num(r)} 0 1,0 {num(2*r)},0 a{num(r)},{num(r)} 0 1,0 {num(-2*r)},0 Z"; s.w*=.66; shapes.append(s); continue
        s.path=xform(s.path,f); s.w=s.w*0.76 if s.kind=='stroke' else s.w; shapes.append(s)
    shapes+=hitodama()
    shapes+=accessories(form)
    return shapes,pal

NIGHT=OUT.replace('/drawable','/drawable-night')
def write_line_pair(name, sh, pal):
    # fixed tints (no theme attributes: some loaders inflate without a theme): dark ink by day, pale ink at night
    os.makedirs(NIGHT,exist_ok=True)
    write(name, sh, pal, line=True, tint='#4A4458')
    write(name, sh, pal, line=True, tint='#E6E0E9', out=NIGHT)

if __name__=='__main__':
    for form in PAL:
        sh,pal=kitsune(form)
        write('kitsune_'+form, sh, pal)
        write_line_pair('kitsune_'+form+'_line', sh, pal)
    print('ok')
