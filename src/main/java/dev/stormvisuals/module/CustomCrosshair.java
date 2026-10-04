package dev.stormvisuals.module;

import com.google.gson.Gson;
import dev.stormvisuals.config.ConfigManager;
import dev.stormvisuals.render.CrosshairRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public final class CustomCrosshair {
    private static final Gson GSON=new Gson();
    private static CrosshairSettings settings=new CrosshairSettings();
    private static long revision;
    private static final CrosshairPulse PULSE=new CrosshairPulse();
    private static Object previousWorld;
    private CustomCrosshair() {}
    public static CrosshairSettings settings(){return settings;}
    public static long revision(){return revision;}
    public static String saveError(){return ConfigManager.autosaveError();}

    public static void initialize() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,original->(g,delta)->{
            Minecraft mc=Minecraft.getInstance();
            if(!settings.enabled || mc.player==null || mc.level==null || mc.player.isSpectator()
                    || !mc.options.getCameraType().isFirstPerson()
                    || mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
                original.render(g,delta);return;
            }
            // The editor draws its preview itself, even when this module is disabled.
            if(mc.screen instanceof dev.stormvisuals.gui.CrosshairEditorScreen)return;
            g.nextStratum();
            float bottom=CrosshairRenderer.draw(g,g.guiWidth()/2f,g.guiHeight()/2f,1,hovered(),pulse(),1);
            CrosshairRenderer.attackIndicator(g,mc,bottom);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.level!=previousWorld || mc.player==null || mc.screen!=null || !settings.enabled){resetPulse();previousWorld=mc.level;}
        });
    }
    public static boolean hovered(){
        Minecraft mc=Minecraft.getInstance();
        return settings.hover && mc.hitResult instanceof EntityHitResult hit
                && (hit.getEntity() instanceof Player || hit.getEntity() instanceof Mob)
                && hit.getEntity().isAlive();
    }
    public static void onSwing(LivingEntity entity,InteractionHand hand){
        if(!entity.level().isClientSide())return;
        Minecraft mc=Minecraft.getInstance();
        if(entity!=mc.player || mc.screen!=null || !settings.enabled || !settings.dynamic || settings.style==CrosshairSettings.Style.V)return;
        if(settings.weaponsOnly && (hand!=InteractionHand.MAIN_HAND || !(entity.getMainHandItem().is(ItemTags.SWORDS)||entity.getMainHandItem().is(ItemTags.AXES))))return;
        triggerPulse();
    }
    public static void triggerPulse(){PULSE.trigger(System.nanoTime());}
    public static void resetPulse(){PULSE.reset();}
    public static float pulse(){return PULSE.value(System.nanoTime());}
    public static void setEnabled(boolean enabled){settings.enabled=enabled;resetPulse();changed();}
    public static void setStyle(CrosshairSettings.Style style){settings.style=style;resetPulse();changed();}
    public static String snapshot(){return GSON.toJson(settings);}
    public static void restore(String json){CrosshairSettings s=GSON.fromJson(json,CrosshairSettings.class);s.validate();settings=s;resetPulse();changed();}
    public static void applySettings(CrosshairSettings loaded){loaded.validate();settings=loaded;resetPulse();revision++;}
    public static void changed(){
        revision++;
        ConfigManager.changed();
    }
    public static void flush(){ConfigManager.flush();}
}
