package dev.stormvisuals.module;

/** Time-based, bounded pulse, shared by gameplay and the editor's test button. */
public final class CrosshairPulse {
    private long start;
    private float from;
    private boolean active;
    public void trigger(long now){from=value(now);start=now;active=true;}
    public void reset(){active=false;from=0;}
    public float value(long now){
        if(!active)return 0;
        float ms=Math.max(0,(now-start)/1_000_000f);
        if(ms>=260){active=false;return 0;}
        if(ms<60)return from+(1-from)*smooth(ms/60);
        return 1-smooth((ms-60)/200);
    }
    private static float smooth(float t){return t*t*(3-2*t);}
}
