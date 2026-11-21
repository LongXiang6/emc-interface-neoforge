package com.lambda.emcinterface;

import com.mojang.serialization.MapCodec;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.DecimalEmc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(EmcInterface.ID)
public final class EmcInterface {
    public static final String ID = "emc_interface";
    private static final BigInteger SLOT_LIMIT = BigInteger.valueOf(1_000_000_000L);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("functional_blocks"));
    private static final net.neoforged.neoforge.registries.DeferredBlock<InterfaceBlock> BLOCK =
            BLOCKS.registerBlock(ID, InterfaceBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    private static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> STACK =
            ITEMS.register(ID, () -> new net.minecraft.world.item.BlockItem(BLOCK.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable(ID + ".tooltip"));
                }
            });
    private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterfaceEntity>> TYPE =
            TYPES.register(ID, () -> BlockEntityType.Builder.of(InterfaceEntity::new, BLOCK.get()).build(null));

    public EmcInterface(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, TYPE.get(), (entity, side) -> entity.handler));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(TAB)) event.accept(STACK.get());
        });
    }

    private static final class InterfaceBlock extends BaseEntityBlock {
        private static final MapCodec<InterfaceBlock> CODEC = simpleCodec(InterfaceBlock::new);

        InterfaceBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new InterfaceEntity(pos, state);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof InterfaceEntity entity) {
                entity.owner = player.getUUID();
                entity.setChanged();
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level.getBlockEntity(pos) instanceof InterfaceEntity entity)) return InteractionResult.FAIL;
            if (!player.getUUID().equals(entity.owner)) {
                player.displayClientMessage(Component.translatable(ID + ".owner_only"), true);
                return InteractionResult.FAIL;
            }
            entity.enabled = !entity.enabled;
            entity.setChanged();
            if (level instanceof ServerLevel serverLevel) serverLevel.invalidateCapabilities(pos);
            player.displayClientMessage(Component.translatable(ID + (entity.enabled ? ".enabled" : ".disabled")), true);
            return InteractionResult.CONSUME;
        }
    }

    private static final class InterfaceEntity extends BlockEntity {
        private UUID owner;
        private boolean enabled;
        private final IItemHandler handler = new TransmutationHandler();

        InterfaceEntity(BlockPos pos, BlockState state) {
            super(TYPE.get(), pos, state);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (owner != null) tag.putUUID("Owner", owner);
            tag.putBoolean("Enabled", enabled);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            enabled = owner != null && tag.getBoolean("Enabled");
        }

        private Session session() {
            if (!enabled || isRemoved() || owner == null || !(level instanceof ServerLevel serverLevel)
                    || !serverLevel.getServer().isSameThread()) return null;
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(owner);
            if (player == null) return null;
            var knowledge = PECapabilities.KNOWLEDGE_CAPABILITY.getCapability(player, null);
            return knowledge != null ? new Session(player, knowledge) : null;
        }

        private final class TransmutationHandler implements IItemHandler {
            // Reserve slot zero for selling. Learned types keep their indices until chunk unload.
            private final List<ItemInfo> slots = new ArrayList<>();
            private final HashSet<ItemInfo> indexed = new HashSet<>();
            private long nextUpdate;

            @Override
            public int getSlots() {
                Session session = session();
                if (session == null) return 0;
                refresh(session.knowledge());
                return slots.size() + 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return extractItem(slot, SLOT_LIMIT.intValue(), true);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                Session session = session();
                if (session == null || stack.isEmpty() || slot < 0 || slot > slots.size()) return stack;
                var proxy = IEMCProxy.INSTANCE;
                ItemInfo item = ItemInfo.fromStack(stack);
                ItemInfo persistent = proxy.getPersistentInfo(item);
                BigInteger value = DecimalEmc.sellValue(stack);
                if (value.signum() <= 0 || slot > 0 && !slots.get(slot - 1).equals(persistent)) return stack;
                if (!simulate) {
                    var knowledge = session.knowledge();
                    boolean learned = knowledge.addKnowledge(persistent);
                    BigInteger next = DecimalEmc.balance(knowledge).add(value.multiply(BigInteger.valueOf(stack.getCount())));
                    DecimalEmc.setBalance(knowledge, next);
                    if (learned) {
                        knowledge.syncKnowledgeChange(session.player(), persistent, true);
                        nextUpdate = 0;
                    }
                    knowledge.syncEmc(session.player());
                }
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                Session session = session();
                if (session == null || slot <= 0 || slot > slots.size() || amount <= 0) return ItemStack.EMPTY;
                ItemInfo item = slots.get(slot - 1);
                var knowledge = session.knowledge();
                BigInteger cost = DecimalEmc.value(item);
                if (cost.signum() <= 0 || !knowledge.hasKnowledge(item)) return ItemStack.EMPTY;
                ItemStack stack = item.createStack();
                int limit = simulate && amount == SLOT_LIMIT.intValue() ? amount : Math.min(amount, stack.getMaxStackSize());
                int count = DecimalEmc.balance(knowledge).divide(cost).min(BigInteger.valueOf(limit)).intValue();
                if (count <= 0) return ItemStack.EMPTY;
                if (!simulate) {
                    DecimalEmc.setBalance(knowledge, DecimalEmc.balance(knowledge).subtract(cost.multiply(BigInteger.valueOf(count))));
                    knowledge.syncEmc(session.player());
                }
                return stack.copyWithCount(count);
            }

            @Override
            public int getSlotLimit(int slot) {
                return SLOT_LIMIT.intValue();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !stack.isEmpty() && session() != null && DecimalEmc.sellValue(stack).signum() > 0
                        && (slot == 0 || slot > 0 && slot <= slots.size() && slots.get(slot - 1)
                        .equals(IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(stack))));
            }

            private void refresh(IKnowledgeProvider knowledge) {
                long now = level.getGameTime();
                if (now < nextUpdate) return;
                nextUpdate = now + 20;
                knowledge.getKnowledge().stream().filter(item -> !indexed.contains(item))
                        .filter(item -> DecimalEmc.value(item).signum() > 0).sorted(Comparator.comparing(ItemInfo::toString))
                        .forEach(item -> {
                            indexed.add(item);
                            slots.add(item);
                        });
            }
        }
    }

    private record Session(ServerPlayer player, IKnowledgeProvider knowledge) {}
}
