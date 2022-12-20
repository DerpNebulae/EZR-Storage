package io.github.gaming32.ezrstorage;

import io.github.gaming32.ezrstorage.block.entity.StorageCoreBlockEntity;
import io.github.gaming32.ezrstorage.gui.StorageCoreScreenHandler;
import io.github.gaming32.ezrstorage.registry.EZRReg;
import io.github.gaming32.ezrstorage.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EZRStorage implements ModInitializer {
    public static final String MOD_ID = "ezrstorage";
    public static final Identifier SYNC_INVENTORY = EZRReg.id("sync_inventory");
    public static final Identifier CUSTOM_SLOT_CLICK = EZRReg.id("custom_slot_click");

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ScreenHandlerType<StorageCoreScreenHandler> STORAGE_CORE_SCREEN_HANDLER = Registry.register(
        Registry.SCREEN_HANDLER,
        EZRReg.id("storage_core"),
        new ScreenHandlerType<>(StorageCoreScreenHandler::new)
    );

    public static final ItemGroup EZR_GROUP = FabricItemGroupBuilder.build(
        new Identifier(MOD_ID, MOD_ID),
        () -> new ItemStack(ModBlocks.STORAGE_CORE.getLeft())
    );

    @Override
    public void onInitialize() {
        EZRReg.registerMod();

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
            !(blockEntity instanceof StorageCoreBlockEntity core) || core.getInventory().getCount() <= 0L
        );

        registerGlobalReceiver(CUSTOM_SLOT_CLICK, (server, player, handler, buf, responseSender) -> {
            final int syncId = buf.readUnsignedByte();
            if (player.currentScreenHandler.syncId != syncId) return;
            final int index = buf.readVarInt();
            final SlotActionType mode = buf.readEnumConstant(SlotActionType.class);
            ((StorageCoreScreenHandler)player.currentScreenHandler).customSlotClick(index, mode);
        });
    }

    private static void registerGlobalReceiver(Identifier packet, ServerPlayNetworking.PlayChannelHandler packetHandler) {
        ServerPlayNetworking.registerGlobalReceiver(packet, (server, player, handler, buf, responseSender) -> {
            if (server.isOnThread()) {
                packetHandler.receive(server, player, handler, buf, responseSender);
            } else {
                final PacketByteBuf newBuf = new PacketByteBuf(buf.copy());
                server.executeSync(() -> {
                    if (handler.getConnection().isOpen()) {
                        try {
                            packetHandler.receive(server, player, handler, newBuf, responseSender);
                        } catch (Exception e) {
                            if (handler.shouldCrashOnException()) {
                                throw e;
                            }

                            EZRStorage.LOGGER.error("Failed to handle packet " + packet + ", suppressing error", e);
                        }
                    } else {
                        EZRStorage.LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                    }
                });
            }
        });
    }
}