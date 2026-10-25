package dev.stormvisuals.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.stormvisuals.config.OfflineSessionAccess;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
public abstract class OfflineSessionMixin implements OfflineSessionAccess {
    @Shadow @Final @Mutable private User user;
    @Shadow @Final @Mutable private CompletableFuture<ProfileResult> profileFuture;
    @Shadow @Final @Mutable private ProfileKeyPairManager profileKeyPairManager;
    @Shadow @Final @Mutable private UserApiService userApiService;
    @Shadow @Final @Mutable private PlayerSocialManager playerSocialManager;
    @Shadow @Final @Mutable private ClientTelemetryManager telemetryManager;
    @Shadow private ReportingContext reportingContext;

    // No session setter/event exists in 1.21.11. Switch only from the disconnected main menu.
    @Override public void stormvisuals$offlineSession(User replacement) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.level != null || client.player != null || client.getConnection() != null)
            throw new IllegalStateException("Нельзя менять сессию в игре");
        user = replacement;
        profileFuture = CompletableFuture.completedFuture(null);
        profileKeyPairManager = ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        userApiService = UserApiService.OFFLINE;
        playerSocialManager = new PlayerSocialManager(client, UserApiService.OFFLINE);
        telemetryManager.close();
        telemetryManager = new ClientTelemetryManager(client, UserApiService.OFFLINE, replacement);
        reportingContext = ReportingContext.create(ReportEnvironment.local(), UserApiService.OFFLINE);
        // Launcher permissions, ownership checks and user properties are deliberately preserved.
    }
}
