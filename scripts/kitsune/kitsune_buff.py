from kitsune_art import *
import kitsune_art as kit2

def star(cx,cy,r,role='sparkle'):
    return S(role,f"M{cx},{cy-r} L{cx+r*.28},{cy-r*.28} L{cx+r},{cy} L{cx+r*.28},{cy+r*.28} L{cx},{cy+r} L{cx-r*.28},{cy+r*.28} L{cx-r},{cy} L{cx-r*.28},{cy-r*.28} Z")

def buff():
    L=[]
    L.append(S('glow',circle(50,50,49),alpha=0.35))
    # nine-tail hint: three fox tails per side behind the body
    for ang in (-52,52,-34,34,-16,16):
        for s in plume(ang,60 if abs(ang)>30 else 62,9,(50,68)):
            if s.role in ('tail','tail2'): s.occ=('buff',)
            L.append(s)
    # jacket hanging from the shoulders
    L+=both('robe',"M18,44 C11,60 8,80 6,100 L27,100 L30,52 Z")
    # torso, neck, traps
    L.append(S('skin',"M24,48 C30,44 42,45 50,47 C58,45 70,44 76,48 L72,64 C70,76 65,88 62,100 L38,100 C35,88 30,76 28,64 Z",occ=()))
    L.append(S('skin',"M30,51 C36,47 42,45 44,40 L56,40 C58,45 64,47 70,51 L64,54 L36,54 Z"))
    L.append(S('pants',"M28,92 L72,92 L78,100 L22,100 Z"))
    L.append(S('red',"M28,87 L72,87 L72,93 L28,93 Z"))
    # arms: double biceps
    for m in (False,True):
        def P(p): return mirror(p) if m else p
        L.append(S('skin',P("M26,44 C16,40 7,46 4,58 C3,64 9,66 14,62 C17,57 23,56 31,58 Z")))
        L.append(S('skin',P("M4,58 C2,46 3,34 7,27 L16,29 C14,37 14,47 14,62 Z")))
        L.append(S('skin',circle(100-11 if m else 11,24,6.5)))
        L.append(S('stroke',P("M7,52 C9,46 15,45 19,48"),'stroke',1.2))
        L.append(S('shade',P("M8,50 C10,44 16,43 20,47 C18,52 12,54 8,50 Z"),alpha=0.35))
    # pecs, abs
    L+=both('shade',"M50,50 C42,48 32,50 28,58 C31,66 43,68 50,62 Z",alpha=0.28)
    L+=both_stroke('stroke',"M29,60 C33,67 44,68 50,63",1.1)
    L.append(S('stroke',"M50,51 L50,90",'stroke',1.1))
    L.append(S('stroke',"M42,73 L58,73 M42,80 L58,80 M43,86 L57,86",'stroke',1.1))
    L+=both_stroke('stroke',"M35,66 C35,78 38,86 41,92",1.1)
    # head: fox ears, hair, face
    L.append(S('hair',"M32,22 C30,10 40,4 50,4 C60,4 70,10 68,22 L71,38 L62,33 L38,33 L29,38 Z"))
    L+=both('hair',"M38,16 L30,-1 L50,9 Z")
    L+=both('innerear',"M39,12 L34,3 L45,8 Z")
    L.append(S('skin',"M35,22 C35,16 65,16 65,22 L65,30 C65,38 58,44 50,44 C42,44 35,38 35,30 Z"))
    L.append(S('hair',"M33,29 C30,14 39,8 50,8 C61,8 70,14 67,29 C65,22 61,18 57,17 L55,25 L51,17 L49,17 L45,25 L43,17 C39,18 35,22 33,29 Z"))
    L+=both('hair',"M34,29 L30,44 L38,37 Z")
    for sx in (1,-1):
        X=(lambda x:x) if sx==1 else (lambda x:100-x)
        L.append(S('iris',ellipse(X(43),30.5,4.0,5.2)))
        L.append(S('pupil',ellipse(X(43),30.8,1.7,3.2)))
        L.append(S('white',circle(X(41.6),28.5,1.5)))
        L.append(S('stroke',f"M{X(37.5)},29 C{X(39)},25 {X(46.5)},24.5 {X(48.5)},28.5",'stroke',2.3))
        L.append(S('stroke',f"M{X(37.8)},28.2 L{X(35.5)},26.4",'stroke',2))
        L.append(S('stroke',f"M{X(38)},23 L{X(47)},25.5",'stroke',1.9))
    L.append(S('stroke',"M50,34 L51.4,36.2",'stroke',1))
    L.append(S('stroke',"M45.5,39.6 C49,41.6 54,40.6 56.6,37.6",'stroke',1.4))
    L.append(S('white',"M53,40.8 L54.7,39.4 L55,41.7 Z"))
    L+=both_stroke('red',"M36,34 L40,35 M36.5,37 L40,37.5",1.3)
    L.append(S('drop',"M72,16 C76.5,22 75.5,27 72,27 C68.5,27 67.5,22 72,16 Z"))
    L.append(star(8,12,6)); L.append(star(92,10,5)); L.append(star(90,60,3.5))
    return L

BPAL=dict(skin='#F6CBA5', hair='#EEF3F5', robe='#1D4E6B', pants='#222C45', red=RED, iris='#FFB02E', white='#FFFFFF', pupil=DARK,
          shade='#33000000', stroke=DARK, innerear='#F7A8B8', tail='#EEF3F5', tail2='#D5E4E9', tailtip='#2BB5A6', glow='#FFF59D',
          line=DARK, drop='#8FD8FF', sparkle='#FFD93B', nose=DARK, cream=CREAM)

# extra occluder for the buff body in line mode
_old=kit2.hidden
def hidden2(x,y,occ):
    if 'buff' in occ:
        if ((x-50)/25)**2+((y-72)/30)**2<1: return True
        if (x<28 or x>72) and 22<y<66: return True
        if y>88: return True
        return False
    return _old(x,y,occ)
kit2.hidden=hidden2

if __name__=='__main__':
    sh=buff()
    import copy
    kit2.write('kitsune_buff', sh, BPAL)
    kit2.write_line_pair('kitsune_buff_line', copy.deepcopy(sh), BPAL)
    print('buff ok')
