#version 330
in vec2 point;
flat in vec2 halfSize;
flat in vec4 fillColor;
flat in vec3 borderColor;
flat in float stroke;
flat in float border;
flat in int kind;
out vec4 fragColor;
float box(vec2 p,vec2 b) {vec2 q=abs(p)-b;return length(max(q,0.0))+min(max(q.x,q.y),0.0);}
float segment(vec2 p,vec2 a,vec2 b) {vec2 ab=b-a;return length(p-a-ab*clamp(dot(p-a,ab)/max(dot(ab,ab),0.0001),0.0,1.0));}
void main() {
    float d;
    float width=min(stroke,2.0*min(halfSize.x,halfSize.y));
    if(kind==1) d=abs(length(point)-max(0.0,halfSize.x-width*0.5))-width*0.5;
    else if(kind==2) d=length(point)-halfSize.x;
    else if(kind==3) d=abs(box(point,max(vec2(0.0),halfSize-width*0.5)))-width*0.5;
    else if(kind==4) d=min(segment(point,vec2(-halfSize.x,-halfSize.y),vec2(0.0)),segment(point,vec2(halfSize.x,-halfSize.y),vec2(0.0)))-stroke*0.5;
    else d=box(point,halfSize);
    float aa=max(fwidth(d),0.0001);
    float total=clamp(0.5-(d-border)/aa,0.0,1.0);
    float fill=clamp(0.5-d/aa,0.0,1.0);
    float alpha=fillColor.a*total;
    if(alpha<=1.0/255.0) discard;
    fragColor=vec4(mix(borderColor,fillColor.rgb,clamp(fill/max(total,0.0001),0.0,1.0)),alpha);
}
