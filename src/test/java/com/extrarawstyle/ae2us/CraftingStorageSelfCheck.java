package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.craftstorage.StorageTier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 高级合成存储器（六档）的结构校验：
 * 1. 档位表：六档容量 256k~16384k（k = 1024）、注册名、颜色码各不相同；
 * 2. 类结构：方块挂 BlockCraftingUnit、瓦片挂 TileCraftingStorageTile（网格 CPU 发现过滤按它判可赋值性）并重写存储契约；
 * 3. 资源：每档 blockstate（四态齐全）、物品模型、三态方块模型与 16x16 贴图、双语名称；
 * 4. 上游形状守卫：AE2UEL"集群容量走瓦片 getStorageBytes""CPU 列表按 TileCraftingStorageTile 发现"的路径还在（变了先在这里红）。
 */
public final class CraftingStorageSelfCheck {
    private static final String PACKAGE = "com/extrarawstyle/ae2us/craftstorage/";
    private static final String BLOCK_CLASS = PACKAGE + "BlockAdvancedCraftingStorage.class";
    private static final String TILE_CLASS = PACKAGE + "TileAdvancedCraftingStorage.class";
    private static final String REGISTRATION_CLASS = PACKAGE + "CraftingStorageRegistration.class";
    private static final int[] EXPECTED_KILOBYTES = {256, 512, 1024, 4096, 8192, 16384};
    private static final String AE2_TILE = "appeng/tile/crafting/TileCraftingTile.class";
    private static final String ITEM_STACK_DESC = "Lnet/minecraft/item/ItemStack;";

    public static void main(final String[] args) throws Exception {
        // 1) 档位表。
        final StorageTier[] tiers = StorageTier.values();
        check(tiers.length == EXPECTED_KILOBYTES.length, "six storage tiers");
        final Set<String> colorCodes = new HashSet<>();
        for (int i = 0; i < tiers.length; i++) {
            final StorageTier tier = tiers[i];
            check(tier.kilobytes() == EXPECTED_KILOBYTES[i], "tier " + i + " kilobytes");
            check(tier.bytes() == EXPECTED_KILOBYTES[i] * 1024, "tier " + i + " bytes");
            check(tier.registryName().equals("crafting_storage_" + EXPECTED_KILOBYTES[i] + "k"),
                    "tier " + i + " registry name");
            check(tier.label().equals(EXPECTED_KILOBYTES[i] + "k"), "tier " + i + " label");
            check(colorCodes.add(tier.colorCode()), "tier " + i + " color code is distinct");
        }

        // 2) 类结构。瓦片父类必须是 TileCraftingStorageTile：CraftingGridCache 重建 CPU 列表时
        //    按 TileCraftingStorageTile.class::isAssignableFrom 过滤机器类，直系 TileCraftingTile 子类会被漏掉。
        check("appeng/block/crafting/BlockCraftingUnit".equals(superName(BLOCK_CLASS)),
                "storage block extends AE2 BlockCraftingUnit");
        check("appeng/tile/crafting/TileCraftingStorageTile".equals(superName(TILE_CLASS)),
                "storage tile extends AE2 TileCraftingStorageTile (grid CPU discovery filter requires it)");
        check(methodExists(TILE_CLASS, "getStorageBytes", "()I")
                        && methodExists(TILE_CLASS, "getItemFromTile", "(Ljava/lang/Object;)" + ITEM_STACK_DESC),
                "storage tile overrides the storage contract");
        check(text(REGISTRATION_CLASS).contains("crafting_storage"),
                "registration carries the TileEntity id");

        // 3) 资源。
        for (final StorageTier tier : tiers) {
            final String base = tier.registryName();
            final String blockstate = text("assets/ae2us/blockstates/" + base + ".json");
            for (final String combo : new String[]{"formed=false,powered=false", "formed=false,powered=true",
                    "formed=true,powered=false", "formed=true,powered=true"}) {
                check(blockstate.contains("\"" + combo + "\""), base + " blockstate lists " + combo);
            }
            check(blockstate.contains("ae2us:" + base + "_unformed")
                            && blockstate.contains("ae2us:" + base + "_formed_off")
                            && blockstate.contains("ae2us:" + base + "_formed_on"),
                    base + " blockstate maps all three states");
            check(text("assets/ae2us/models/item/" + base + ".json")
                            .contains("ae2us:block/" + base + "_unformed"),
                    base + " item model points at the unformed block model");
            for (final String state : new String[]{"unformed", "formed_off", "formed_on"}) {
                check(text("assets/ae2us/models/block/" + base + "_" + state + ".json")
                                .contains("ae2us:blocks/" + base + "_" + state),
                        base + "_" + state + " block model points at its texture");
                check(texture("assets/ae2us/textures/blocks/" + base + "_" + state + ".png"),
                        base + "_" + state + " texture is a 16x16 png");
            }
        }
        final String zh = text("assets/ae2us/lang/zh_cn.lang");
        final String en = text("assets/ae2us/lang/en_us.lang");
        for (final StorageTier tier : tiers) {
            final String key = "tile.ae2us." + tier.registryName() + ".name";
            check(langLine(zh, key).contains(tier.label()) && langLine(zh, key).contains("合成存储器"),
                    tier.registryName() + " zh name");
            check(langLine(en, key).contains(tier.label()) && langLine(en, key).contains("Crafting Storage"),
                    tier.registryName() + " en name");
        }

        // 4) 上游形状：AE2UEL 换版本先在这里红。
        check(publicNonFinal(AE2_TILE), "TileCraftingTile is public and extendable");
        check(methodExists(AE2_TILE, "getStorageBytes", "()I"), "TileCraftingTile.getStorageBytes still exists");
        check(methodExists(AE2_TILE, "isStorage", "()Z"), "TileCraftingTile.isStorage still exists");
        Class.forName("appeng.block.crafting.BlockCraftingUnit$CraftingUnitType").getField("STORAGE_1K");
        check(invokesAndWrites("appeng/me/cluster/implementations/CraftingCPUCluster.class", "addTile",
                        "getStorageBytes", "availableStorage"),
                "cluster addTile still accounts storage via getStorageBytes");
        check(updateCpuClustersFiltersByStorageTile(),
                "CraftingGridCache still discovers CPUs via TileCraftingStorageTile assignability");

        System.out.println("Crafting storage checks passed (tiers, classes, resources, upstream shape)");
    }

    /** CraftingGridCache.updateCPUClusters() 的发现过滤：同一方法里"取 TileCraftingStorageTile 类常量 + isAssignableFrom lambda"两条都在才算数。 */
    private static boolean updateCpuClustersFiltersByStorageTile() throws Exception {
        final String storageTileClass = "appeng/tile/crafting/TileCraftingStorageTile";
        final boolean[] takenAsConstant = {false};
        final boolean[] assignable = {false};
        new ClassReader(bytes("appeng/me/cache/CraftingGridCache.class")).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName, final String methodDesc,
                    final String signature, final String[] exceptions) {
                if (!"updateCPUClusters".equals(methodName) || !"()V".equals(methodDesc)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (value instanceof Type && storageTileClass.equals(((Type) value).getInternalName())) {
                            takenAsConstant[0] = true;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(final String name, final String descriptor,
                            final Handle bootstrap, final Object... bootstrapArgs) {
                        for (final Object arg : bootstrapArgs) {
                            if (arg instanceof Handle && "java/lang/Class".equals(((Handle) arg).getOwner())
                                    && "isAssignableFrom".equals(((Handle) arg).getName())) {
                                assignable[0] = true;
                            }
                        }
                    }
                };
            }
        }, 0);
        return takenAsConstant[0] && assignable[0];
    }

    private static byte[] bytes(final String resource) throws Exception {
        try (InputStream in = CraftingStorageSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError("resource not found on classpath: " + resource);
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static String text(final String resource) throws Exception {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    private static String superName(final String resource) throws Exception {
        return new ClassReader(bytes(resource)).getSuperName();
    }

    private static boolean publicNonFinal(final String resource) throws Exception {
        final int access = new ClassReader(bytes(resource)).getAccess();
        return (access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_FINAL) == 0;
    }

    private static boolean methodExists(final String resource, final String name, final String desc) throws Exception {
        final boolean[] found = {false};
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName, final String methodDesc,
                    final String signature, final String[] exceptions) {
                if (name.equals(methodName) && desc.equals(methodDesc)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return found[0];
    }

    /** 指定方法里同时出现"调用了某方法"与"写了某字段"（集群把容量记进 availableStorage 的形状守卫）。 */
    private static boolean invokesAndWrites(final String resource, final String methodName,
            final String invokedName, final String fieldName) throws Exception {
        final boolean[] invoked = {false};
        final boolean[] written = {false};
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                if (!methodName.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(final int opcode, final String owner, final String invokedMethod,
                            final String invokedDesc, final boolean itf) {
                        if (invokedName.equals(invokedMethod)) {
                            invoked[0] = true;
                        }
                    }

                    @Override
                    public void visitFieldInsn(final int opcode, final String owner, final String field,
                            final String fieldDesc) {
                        if (opcode == Opcodes.PUTFIELD && fieldName.equals(field)) {
                            written[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return invoked[0] && written[0];
    }

    private static boolean texture(final String resource) throws Exception {
        try (InputStream in = CraftingStorageSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            final BufferedImage image = ImageIO.read(in);
            return image != null && image.getWidth() == 16 && image.getHeight() == 16;
        }
    }

    private static String langLine(final String text, final String key) {
        for (final String line : text.split("\n")) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1);
            }
        }
        throw new AssertionError("lang key not found: " + key);
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
