package dev.stormvisuals.module;

import com.google.gson.Gson;
import java.util.ArrayList;

/** Standalone model and animation checks; no test framework or game window required. */
public final class CrosshairChecks {
    public static void main(String[] args){
        Gson gson=new Gson();
        CrosshairSettings s=new CrosshairSettings();
        check(s.elements.size()==4,"Default custom cross has four arms");
        s.style=CrosshairSettings.Style.CUSTOM;s.enabled=true;s.hover=true;
        s.elements.get(0).ownSettings=true;s.elements.get(0).color=0x123456;
        s.elements.get(0).opacity=37;s.elements.get(0).outlineWidth=2.5f;
        String json=gson.toJson(s);CrosshairSettings copy=gson.fromJson(json,CrosshairSettings.class);copy.validate();
        check(copy.style==s.style&&copy.enabled&&copy.hover,"Global settings round trip");
        check(copy.elements.get(0).color==0x123456&&copy.elements.get(0).opacity==37&&copy.elements.get(0).outlineWidth==2.5f,"Individual styling round trip");
        check(gson.toJson(copy).equals(json),"Stable history snapshot round trip");
        copy.elements=new ArrayList<>();copy.elements.add(null);
        for(int i=0;i<40;i++){var e=new CrosshairSettings.Element();e.x=Float.NaN;e.y=999;e.size=Float.POSITIVE_INFINITY;e.kind=null;copy.elements.add(e);}
        copy.opacity=-10;copy.outlineWidth=99;copy.style=null;copy.squareSize=Float.NaN;copy.validate();
        check(copy.elements.size()==32,"Element count limit and null entries");
        check(copy.elements.get(0).x==0&&copy.elements.get(0).y==64&&copy.elements.get(0).size==5,"Nonfinite and out-of-range values");
        check(copy.opacity==0&&copy.outlineWidth==4&&copy.style==CrosshairSettings.Style.SQUARE,"Shared settings validation");
        CrosshairSettings partial=gson.fromJson("{\"style\":\"unknown\",\"elements\":null}",CrosshairSettings.class);partial.validate();
        check(partial.style==CrosshairSettings.Style.SQUARE&&partial.elements.size()==4,"Partial/unknown settings fallback");
        CrosshairPulse p=new CrosshairPulse();p.trigger(0);
        near(p.value(0),0,"Pulse starts at rest");near(p.value(60_000_000),1,"60 ms peak");near(p.value(160_000_000),.5f,"200 ms smooth return");near(p.value(260_000_000),0,"Pulse ends at rest");
        p.trigger(1_000_000_000);float before=p.value(1_100_000_000);p.trigger(1_100_000_000);near(p.value(1_100_000_000),before,"Retrigger continuity");
        for(int i=0;i<1000;i++){long now=1_100_000_000L+i*1_000_000L;if(i%20==0)p.trigger(now);float v=p.value(now);check(v>=0&&v<=1,"Repeated swings cannot accumulate expansion");}
        p.reset();near(p.value(2_000_000_000),0,"Reset clears animation");
        CrosshairPulse a=new CrosshairPulse(),b=new CrosshairPulse();a.trigger(0);b.trigger(0);
        for(int i=0;i<150;i++)a.value(i*1_000_000L);
        near(a.value(150_000_000),b.value(150_000_000),"Independent of render sampling/FPS");
        System.out.println("Crosshair model and pulse checks passed");
    }
    private static void near(float a,float b,String message){check(Math.abs(a-b)<.0001f,message);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
