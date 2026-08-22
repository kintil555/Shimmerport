package com.lowdragmc.shimmer.client.shader;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.http.util.Asserts;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ReloadShaderManager {

    private static Map<Identifier, Resource> reloadResources = new HashMap<>();
    public static boolean isReloading = false;
    private static boolean foreReloadAll = false;
    private static final ResourceProvider reloadShaderResource = (res) -> Optional.of(reloadResources.get(res));
    public static PreparableReloadListener shaderReloader;

    private static void recordResource(Identifier resourceLocation, Resource resource) {
        reloadResources.put(resourceLocation, resource);
    }

    public static void cleanResource() {
        message(Component.literal("clear all resource for backup usage"));
        reloadResources.clear();
    }

    private static void recordCopyResource(Identifier resourceLocation, Resource resource) {
        try(var res = resource.open()) {
            final byte[] data = res.readAllBytes();
            final IoSupplier<InputStream> ioSupplier = () -> new ByteArrayInputStream(data);
            Resource copyResource = new Resource(resource.source(), ioSupplier);
            recordResource(resourceLocation, copyResource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void reloadShader() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resourceManager = minecraft.getResourceManager();
        PreparableReloadListener.PreparationBarrier barrier = CompletableFuture::completedFuture;
        ProfilerFiller profilerFiller = InactiveProfiler.INSTANCE;
        Executor backgroundExecutor = Util.backgroundExecutor();
        Executor gameExecutor = Minecraft.getInstance();
        message(Component.literal("start reloading shader"));
        long time = System.currentTimeMillis();
        Map<Identifier, Resource> backupResource = reloadResources;
        reloadResources = new HashMap<>();
        isReloading = true;
        foreReloadAll = false;
        try {
            Asserts.notNull(shaderReloader,"shader reloader hasn't be set up");
            shaderReloader.reload(barrier,resourceManager,profilerFiller,profilerFiller,backgroundExecutor,gameExecutor);
            minecraft.levelRenderer.onResourceManagerReload(resourceManager);
            message(Component.literal("reload success"));
            message(Component.literal(MessageFormat.format("cache resource:{0}", reloadResources.size())));
            message(Component.literal(MessageFormat.format("total time cost:{0}s", (System.currentTimeMillis() - time) / 1000f)));
            backupResource.clear();
        } catch (Exception tryException) {
            foreReloadAll = true;
            reloadResources.clear();
            reloadResources = backupResource;
            message(Component.literal("exception occur will reloading , trying to backup").withStyle(ChatFormatting.RED));
            message(Component.literal(MessageFormat.format("error:{0}", tryException.getMessage())).withStyle(ChatFormatting.RED));
            try {
                shaderReloader.reload(barrier,resourceManager,profilerFiller,profilerFiller,backgroundExecutor,gameExecutor);
                minecraft.levelRenderer.onResourceManagerReload(resourceManager);
                message(Component.literal("load backup resource successful"));
            } catch (Exception backupException) {
                message(Component.literal("exception occur while trying backup").withStyle(ChatFormatting.RED));
                message(Component.literal(MessageFormat.format("error:{0}", backupException.getMessage())).withStyle(ChatFormatting.RED));
                backupException.addSuppressed(tryException);
                throw backupException;
            }
        } finally {
            isReloading = false;
            foreReloadAll = false;
        }
    }

    private static void message(Component component) {
        Minecraft.getInstance().player.sendSystemMessage(component);
    }

    @NotNull
    public static ShaderInstance backupNewShaderInstance(ResourceProvider resourceProvider, String shaderName, VertexFormat vertexFormat) throws IOException {
        if (foreReloadAll) {
            return new ShaderInstance(reloadShaderResource, shaderName, vertexFormat);
        }
        ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, shaderName, vertexFormat);
        Identifier shaderResourceLocation = new Identifier(shaderName);
        recordProgramResource(resourceProvider, shaderResourceLocation.getNamespace(), shaderResourceLocation.getPath());
        return shaderInstance;
    }

    public static ShaderInstance backupNewShaderInstance(ResourceProvider resourceProvider, Identifier shaderLocation, VertexFormat vertexFormat) throws IOException {
        return backupNewShaderInstance(resourceProvider, shaderLocation.toString(), vertexFormat);
    }

    private static void recordProgramResource(ResourceProvider resourceProvider, String nameSpace, String shaderName) throws IOException {
        Identifier programResourceLocation = new Identifier(nameSpace, "shaders/core/" + shaderName + ".json");
        Resource programResource = resourceProvider.getResource(programResourceLocation).orElseThrow();
        ReloadShaderManager.recordCopyResource(programResourceLocation, programResource);
        JsonObject jsonObject = GsonHelper.parse(new InputStreamReader(resourceProvider.getResource(programResourceLocation).orElseThrow().open(), StandardCharsets.UTF_8));
        Identifier vertex = new Identifier(GsonHelper.getAsString(jsonObject, "vertex"));
        Identifier vertexResourceLocation = new Identifier(vertex.getNamespace(), "shaders/core/" + vertex.getPath() + ".vsh");
        ReloadShaderManager.recordCopyResource(vertexResourceLocation, resourceProvider.getResource(vertexResourceLocation).orElseThrow());
        Identifier fragment = new Identifier(GsonHelper.getAsString(jsonObject, "fragment"));
        Identifier fragmentResourceLocation = new Identifier(fragment.getNamespace(), "shaders/core/" + fragment.getPath() + ".fsh");
        ReloadShaderManager.recordCopyResource(fragmentResourceLocation, resourceProvider.getResource(fragmentResourceLocation).orElseThrow());
    }

    public static ResourceManager reloadResourceManager = new ResourceManager() {
        @Override
        public Set<String> getNamespaces() {
            return Set.of();
        }

        @Override
        public List<Resource> getResourceStack(Identifier location) {
            return List.of(getResource(location).orElseThrow());
        }

        @Override
        public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) {
            return Map.of();
        }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) {
            return Map.of();
        }

        @Override
        public Optional<Resource> getResource(Identifier location) {
            return Optional.of(reloadResources.get(location));
        }

        @Override
        public Resource getResourceOrThrow(Identifier resourceLocation) throws FileNotFoundException {
            try {
                return Objects.requireNonNull(reloadResources.get(resourceLocation));
            }catch (NullPointerException e){
                throw new FileNotFoundException("resourceLocation:" + resourceLocation + " is not stored");
            }
        }

        @Override
        public InputStream open(Identifier resourceLocation) throws IOException {
            return reloadResources.get(resourceLocation).open();
        }

        @Override
        public BufferedReader openAsReader(Identifier resourceLocation) throws IOException {
            return reloadResources.get(resourceLocation).openAsReader();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return null;
        }
    };

    public static PostChain backupNewPostChain(TextureManager textureManager, ResourceManager resourceManager, RenderTarget renderTarget, Identifier resourceLocation) throws IOException {
        if (foreReloadAll) {
            return new PostChain(textureManager, reloadResourceManager, renderTarget, resourceLocation);
        }
        PostChain postChain = new PostChain(textureManager, resourceManager, renderTarget, resourceLocation);
        recordPostChainResource(resourceManager, resourceLocation);
        return postChain;
    }

    private static void recordPostChainResource(ResourceManager resourceManager, Identifier resourceLocation) throws IOException {
        Resource postChainResource = resourceManager.getResource(resourceLocation).orElseThrow();
        recordCopyResource(resourceLocation, postChainResource);
    }

    public static EffectInstance backupNewEffectInstance(ResourceManager resourceProvider, String shaderName) throws IOException {
        if (foreReloadAll) {
            return new EffectInstance(reloadResourceManager, shaderName);
        }
        EffectInstance effectInstance = new EffectInstance(resourceProvider, shaderName);
        recordEffectInstanceResource(resourceProvider, shaderName);
        return effectInstance;
    }

    private static void recordEffectInstanceResource(ResourceManager resourceProvider, String shaderName) throws IOException {
        Identifier resourceLocation = make(Identifier.tryParse(shaderName), (rl) ->
                new Identifier(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".json"));
        Resource effectResource = resourceProvider.getResource(resourceLocation).orElseThrow();
        recordCopyResource(resourceLocation, effectResource);
        JsonObject effectJsonObject = GsonHelper.parse(new InputStreamReader(resourceProvider.getResource(resourceLocation).orElseThrow().open(), StandardCharsets.UTF_8));
        String vertex = GsonHelper.getAsString(effectJsonObject, "vertex");
        Identifier vertexResourceLocation = make(Identifier.tryParse(vertex), rl ->
                new Identifier(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".vsh"));
        recordCopyResource(vertexResourceLocation, resourceProvider.getResource(vertexResourceLocation).orElseThrow());
        String fragment = GsonHelper.getAsString(effectJsonObject, "fragment");
        Identifier fragmentResourceLocation = make(Identifier.tryParse(fragment), rl ->
                new Identifier(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".fsh"));
        recordCopyResource(fragmentResourceLocation, resourceProvider.getResource(fragmentResourceLocation).orElseThrow());
    }

    private static <T, R> R make(T origin, Function<T, R> transformer) {
        return transformer.apply(origin);
    }
}