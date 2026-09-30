package dev.stormvisuals.editor;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Imports user PNGs and uploads them to the running client's texture manager. */
final class ImportedImages {
    private static final Logger LOGGER = LoggerFactory.getLogger("StormVisuals PNG");
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final int MAX_SIDE = 4096;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("stormvisuals/images");
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "StormVisuals PNG IO");
        thread.setDaemon(false);
        return thread;
    });
    private final Map<String, Identifier> loaded = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final AtomicBoolean choosing = new AtomicBoolean();

    void choose(Minecraft client, Consumer<Imported> done) {
        if (!choosing.compareAndSet(false, true)) return;
        io.execute(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.pointers(stack.UTF8("*.png"));
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Выбрать PNG для StormVisuals", "", filter, "PNG (*.png)", false);
                if (selected == null) return;
                Path source = Path.of(selected);
                String name = UUID.randomUUID().toString().replace("-", "") + ".png";
                Path target = directory.resolve(name);
                try {
                    if (Files.size(source) > MAX_FILE_BYTES) throw new IOException("PNG is too large");
                    checkPng(source);
                    Files.createDirectories(directory);
                    try (InputStream input = Files.newInputStream(source);
                         NativeImage image = NativeImage.read(input)) {
                        validate(image);
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        int width = image.getWidth();
                        int height = image.getHeight();
                        client.execute(() -> {
                            request(client, name);
                            done.accept(new Imported(name, width, height));
                        });
                    }
                } catch (IOException | RuntimeException error) {
                    LOGGER.warn("Could not import PNG {}", source, error);
                    client.execute(() -> message(client, "Не удалось загрузить PNG: " + error.getMessage()));
                }
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("Could not open PNG file chooser", error);
                client.execute(() -> message(client, "Не удалось открыть выбор PNG: " + error.getMessage()));
            } finally {
                choosing.set(false);
            }
        });
    }

    void draw(Minecraft client, GuiGraphics graphics, CanvasDocument.ImageItem item) {
        Identifier texture = loaded.get(item.file());
        if (texture == null) {
            request(client, item.file());
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                Math.round(item.x()), Math.round(item.y()), 0, 0,
                Math.round(item.width()), Math.round(item.height()),
                Math.round(item.width()), Math.round(item.height()));
    }

    void close(Minecraft client) {
        io.shutdownNow();
        for (Identifier texture : loaded.values()) {
            client.getTextureManager().release(texture);
        }
        loaded.clear();
    }

    private void request(Minecraft client, String name) {
        if (loaded.containsKey(name) || failed.contains(name) || !loading.add(name)) return;
        io.execute(() -> {
            Path file = directory.resolve(name);
            try {
                if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("PNG is too large");
                checkPng(file);
                NativeImage image;
                try (InputStream input = Files.newInputStream(file)) {
                    image = NativeImage.read(input);
                }
                try {
                    validate(image);
                } catch (IOException error) {
                    image.close();
                    throw error;
                }
                client.execute(() -> {
                    try {
                        Identifier id = Identifier.fromNamespaceAndPath("stormvisuals", "imported/" + name);
                        client.getTextureManager().register(id, new DynamicTexture(() -> "StormVisuals " + name, image));
                        loaded.put(name, id);
                    } catch (RuntimeException error) {
                        image.close();
                        LOGGER.warn("Could not upload PNG {}", file, error);
                    } finally {
                        loading.remove(name);
                    }
                });
            } catch (IOException | RuntimeException error) {
                LOGGER.warn("Could not load PNG {}", file, error);
                client.execute(() -> {
                    loading.remove(name);
                    failed.add(name);
                });
            }
        });
    }

    private static void validate(NativeImage image) throws IOException {
        if (image.getWidth() < 1 || image.getHeight() < 1
                || image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
            throw new IOException("PNG dimensions must be 1.." + MAX_SIDE);
        }
    }

    private static void checkPng(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            if (!Arrays.equals(input.readNBytes(PNG_SIGNATURE.length), PNG_SIGNATURE)) {
                throw new IOException("Selected file is not a PNG");
            }
        }
    }

    private static void message(Minecraft client, String text) {
        if (client.player != null) client.player.displayClientMessage(Component.literal(text), false);
    }

    record Imported(String file, int width, int height) {
    }
}
