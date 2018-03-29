package terrails.statskeeper.event;

import com.google.common.base.CharMatcher;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.StringUtils;
import terrails.statskeeper.Constants;
import terrails.statskeeper.api.capabilities.health.IHealth;
import terrails.statskeeper.config.ConfigHandler;
import terrails.statskeeper.data.capabilities.health.CapabilityHealth;
import terrails.statskeeper.data.world.CustomWorldData;
import terrails.statskeeper.potion.ModPotions;

import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber
public class HealthEvent {

    private static UUID STATS_KEEPER_HEALTH_UUID = Constants.STATS_KEEPER_HEALTH_UUID;

    /**
     * Enable debugging of these events:
     * {@link HealthEvent#firstJoin} {@link HealthEvent#onTick} {@link HealthEvent#onClone}
     * {@link HealthEvent#addHealthOnRightClick} {@link HealthEvent#addHealthOnUsedItem} {@link HealthEvent#addHealthOnItem}
     */
    private static final boolean ENABLE_DEBUGGING = false;
    private static void debugMessage(String event, String string) {
        if (ENABLE_DEBUGGING) {
            Constants.getLogger(event).info(string);
        }
    }

    private static Item getItem(String item) {
        String one = item.contains(";") ? StringUtils.substringBefore(item, ";") : item.contains(",") ? StringUtils.substringBefore(item, ",") : item;
        return Item.getByNameOrId(one);
    }
    private static int getItemMeta(String item) {
        String one = item.contains(";") ? StringUtils.substringAfter(item ,";") : "0";
        String two = one.contains(",") ? StringUtils.substringBefore(item, ",") : one;
        int meta = Integer.parseInt(CharMatcher.digit().retainFrom(two));
        return meta;
    }
    private static int getItemAddedHealth(String item) {
        return item.contains(", ") ? Integer.parseInt(CharMatcher.digit().retainFrom(StringUtils.substringAfter(item, ","))) : 2;
    }

    public static void setMaxHealth(EntityPlayer player, UUID modifierUUID, double health) {
        setMaxHealth(player, modifierUUID, health, "StatsKeeper HP");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.WorldTickEvent event) {
        if (!event.world.isRemote && event.phase == TickEvent.Phase.END) {
            for (EntityPlayer player : event.world.playerEntities) {

                IHealth health = player.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
                CustomWorldData worldData = CustomWorldData.get(player.getEntityWorld());

                if (health != null && worldData != null) {

                    if (ConfigHandler.healthSystem) {
                        worldData.markDirty();

                        if (!ConfigHandler.startWithMinHealth) {
                            if (worldData.getOldMaxHealth() != worldData.getMaxHealth() || !health.getHasAddedHealth()) {
                                health.setAddedHealth(worldData.getMaxHealth() - getMaxHealthAttribute(player).getBaseValue());
                                worldData.setOldMaxHealth(worldData.getMaxHealth());
                                setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                                health.setHasAddedHealth(true);
                                TextComponentTranslation text = new TextComponentTranslation("change.health", (int) worldData.getMaxHealth());
                                if (!text.getFormattedText().isEmpty() && ConfigHandler.healthMessage) Constants.playerMessage(player, text.getFormattedText());
                                debugMessage("ServerTickEvent", "Health has been changed to: " + health.getAddedHealth());
                            }
                        } else {
                            if (worldData.getOldMinHealth() != worldData.getMinHealth() || !health.getHasAddedHealth()) {
                                health.setAddedHealth(worldData.getMinHealth() - getMaxHealthAttribute(player).getBaseValue());
                                worldData.setOldMinHealth(worldData.getMinHealth());
                                setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                                health.setHasAddedHealth(true);
                                TextComponentTranslation text = new TextComponentTranslation("change.health", (int) worldData.getMinHealth());
                                if (!text.getFormattedText().isEmpty() && ConfigHandler.healthMessage) Constants.playerMessage(player, text.getFormattedText());
                                debugMessage("ServerTickEvent", "Health has been changed to: " + health.getAddedHealth());
                            }
                        }
                        if (worldData.getOldMinHealth() != worldData.getMinHealth() && worldData.getOldMinHealth() < worldData.getMinHealth()) {
                            health.setAddedHealth(worldData.getMinHealth() - getMaxHealthAttribute(player).getBaseValue());
                            worldData.setOldMinHealth(worldData.getMinHealth());
                            setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                            health.setHasAddedHealth(true);
                            TextComponentTranslation text = new TextComponentTranslation("change.health", (int) worldData.getMinHealth());
                            if (!text.getFormattedText().isEmpty() && ConfigHandler.healthMessage) Constants.playerMessage(player, text.getFormattedText());
                            debugMessage("ServerTickEvent", "Health has been changed to: " + health.getAddedHealth());
                        }
                    } else {
                        removeMaxHealthModifier(player, STATS_KEEPER_HEALTH_UUID);
                        health.setHasAddedHealth(false);
                        debugMessage("ServerTickEvent", "Health System is disabled");
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void firstJoin(PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        IHealth health = player.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
        CustomWorldData worldData = CustomWorldData.get(player.getEntityWorld());

        if (health != null && worldData != null && ConfigHandler.healthSystem) {
            worldData.markDirty();

            if (!health.getHasAddedHealth()) {
                debugMessage("PlayerLoggedInEvent", "Starting Health Before Setting: " + player.getMaxHealth());
                if (!ConfigHandler.startWithMinHealth) {
                    worldData.setOldMaxHealth(worldData.getMaxHealth());
                    health.setAddedHealth(worldData.getMaxHealth() - getMaxHealthAttribute(player).getBaseValue());
                    setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                    health.setHasAddedHealth(true);
                } else {
                    worldData.setOldMinHealth(worldData.getMinHealth());
                    health.setAddedHealth(worldData.getMinHealth() - getMaxHealthAttribute(player).getBaseValue());
                    setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                    health.setHasAddedHealth(true);
                }

                TextComponentTranslation text = new TextComponentTranslation("change.health", (int) (!ConfigHandler.startWithMinHealth ? worldData.getMaxHealth() : worldData.getMinHealth()));
                if (!text.getFormattedText().isEmpty() && ConfigHandler.healthMessage) Constants.playerMessage(player, text.getFormattedText());

                debugMessage("PlayerLoggedInEvent", "Starting Health After Setting: " + player.getMaxHealth());
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        if (ConfigHandler.healthSystem) {
            EntityPlayer oldPlayer = event.getOriginal();
            EntityPlayer newPlayer = event.getEntityPlayer();
            IHealth oldHealth = oldPlayer.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
            IHealth newHealth = newPlayer.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
            CustomWorldData worldData = CustomWorldData.get(newPlayer.getEntityWorld());
            if (oldHealth != null && worldData != null && newHealth != null && oldHealth.getHasAddedHealth()) {
                worldData.markDirty();

                if (worldData.getMaxHealth() != 0 && worldData.getMinHealth() == 0 && worldData.getRemoveHealth() == 0) {
                    newHealth.setAddedHealth(worldData.getMaxHealth() - getMaxHealthAttribute(newPlayer).getBaseValue());
                    setMaxHealth(newPlayer, STATS_KEEPER_HEALTH_UUID, newHealth.getAddedHealth());
                } 
                
                if (worldData.getMaxHealth() != 0 && worldData.getMinHealth() != 0) {
                    debugMessage("PlayerEvent.Clone", "Added Health Before Death: " + oldHealth.getAddedHealth());

                    double removedHealth = oldHealth.getAddedHealth() - worldData.getRemoveHealth();
                    double addedHealth = removedHealth <= worldData.getMinHealth() - getMaxHealthAttribute(newPlayer).getBaseValue() ? worldData.getMinHealth() - getMaxHealthAttribute(newPlayer).getBaseValue() : removedHealth;
                    newHealth.setAddedHealth(addedHealth);
                    setMaxHealth(newPlayer, STATS_KEEPER_HEALTH_UUID, newHealth.getAddedHealth());

                    int removedAmount = (int) (oldHealth.getAddedHealth() - newHealth.getAddedHealth());

                    if (ConfigHandler.healthMessage && worldData.getRemoveHealth() != 0) {
                        if (removedAmount != 0) {
                            TextComponentTranslation text = new TextComponentTranslation("death.removed_amount.1", removedAmount);
                            if (!text.getFormattedText().isEmpty()) Constants.playerMessage(newPlayer, text.getFormattedText());
                        } else {
                            TextComponentTranslation text = new TextComponentTranslation("death.removed_amount.2");
                            if (!text.getFormattedText().isEmpty()) Constants.playerMessage(newPlayer, text.getFormattedText());
                        }
                    }
                    debugMessage("PlayerEvent.Clone", "Added Health After Death: " + newHealth.getAddedHealth());
                }
            }
        }
    }

    @SubscribeEvent
    public static void addHealthOnRightClick(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            World world = player.getEntityWorld();
            IHealth health = player.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
            CustomWorldData worldData = CustomWorldData.get(player.getEntityWorld());
            if (ConfigHandler.healthSystem && health != null && worldData != null) {
                worldData.markDirty();
                Item theItem = getItem(health.getLastItemName());
                int meta = getItemMeta(health.getLastItemName());
                int healthAmount = getItemAddedHealth(health.getLastItemName());

                if (theItem != null && event.getDuration() == 0) {
                    debugMessage("LivingEntityUseItemEvent.Start", "ItemStack: " + theItem + ", Metadata: " + meta);
                    debugMessage("LivingEntityUseItemEvent.Start", "Health Regained: " + healthAmount);

                    if (!world.isRemote && world instanceof WorldServer) {
                        if (worldData.getMaxHealth() <= player.getMaxHealth() + healthAmount) {
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health Before Item: " + health.getAddedHealth());
                            health.setAddedHealth(worldData.getMaxHealth() - getMaxHealthAttribute(player).getBaseValue());
                            setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                            event.getItem().shrink(1);
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health After Item: " + health.getAddedHealth());
                        } else {
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health Before Item: " + health.getAddedHealth());
                            health.setAddedHealth((player.getMaxHealth() + healthAmount) - getMaxHealthAttribute(player).getBaseValue());
                            setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                            event.getItem().shrink(1);
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health After Item: " + health.getAddedHealth());
                        }
                    } else if (!(worldData.getMaxHealth() >= player.getMaxHealth())) {
                        Random random = new Random();
                        for (int particles = 0; particles <= 10; particles++)
                            world.spawnParticle(EnumParticleTypes.HEART,
                                    player.posX + (random.nextDouble() - 0.4D) * (double) player.width,
                                    player.posY + random.nextDouble() * (double) player.height - player.getYOffset(),
                                    player.posZ + (random.nextDouble() - 0.4D) * (double) player.width,
                                    1.0D, 0.2D, 1.0D);
                        world.playSound(player, player.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.75F, 1.0F);
                    }
                    health.setLastItemName("none");
                }
            }
        }
    }
    @SubscribeEvent
    public static void addHealthOnUsedItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            World world = player.getEntityWorld();
            IHealth health = player.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
            CustomWorldData worldData = CustomWorldData.get(player.getEntityWorld());
            if (ConfigHandler.healthSystem && health != null && worldData != null && !health.getLastItemName().equals("none")) {
                worldData.markDirty();
                Item theItem = getItem(health.getLastItemName());
                int meta = getItemMeta(health.getLastItemName());
                int healthAmount = getItemAddedHealth(health.getLastItemName());
                if (theItem != null) {
                    debugMessage("LivingEntityUseItemEvent.Start", "ItemStack: " + theItem + ", Metadata: " + meta);
                    debugMessage("LivingEntityUseItemEvent.Start", "Health Regained: " + healthAmount);

                    if (!world.isRemote && world instanceof WorldServer) {
                        if (worldData.getMaxHealth() <= player.getMaxHealth() + healthAmount) {
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health Before Item: " + health.getAddedHealth());
                            health.setAddedHealth(worldData.getMaxHealth() - getMaxHealthAttribute(player).getBaseValue());
                            setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                            event.getItem().shrink(1);
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health After Item: " + health.getAddedHealth());
                        } else {
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health Before Item: " + health.getAddedHealth());
                            health.setAddedHealth((player.getMaxHealth() + healthAmount) - getMaxHealthAttribute(player).getBaseValue());
                            setMaxHealth(player, STATS_KEEPER_HEALTH_UUID, health.getAddedHealth());
                            event.getItem().shrink(1);
                            debugMessage("LivingEntityUseItemEvent.Start", "Added Health After Item: " + health.getAddedHealth());
                        }
                    } else if (!(worldData.getMaxHealth() >= player.getMaxHealth())) {
                        Random random = new Random();
                        for (int particles = 0; particles <= 10; particles++)
                            world.spawnParticle(EnumParticleTypes.HEART,
                                    player.posX + (random.nextDouble() - 0.4D) * (double) player.width,
                                    player.posY + random.nextDouble() * (double) player.height - player.getYOffset(),
                                    player.posZ + (random.nextDouble() - 0.4D) * (double) player.width,
                                    1.0D, 0.2D, 1.0D);
                        world.playSound(player, player.getPosition(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.75F, 1.0F);
                    }
                    health.setLastItemName("none");
                }
            }
        }
    }
    @SubscribeEvent
    public static void addHealthOnItem(PlayerInteractEvent.RightClickItem event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = player.getEntityWorld();
        IHealth health = player.getCapability(CapabilityHealth.HEALTH_CAPABILITY, null);
        CustomWorldData worldData = CustomWorldData.get(player.getEntityWorld());
        if (ConfigHandler.healthSystem && health != null && worldData != null && !(player.getMaxHealth() >= worldData.getMaxHealth())) {
            for (String item : ConfigHandler.itemNameArray) {

                Item theItem = getItem(item);
                int meta = getItemMeta(item);

                if (theItem != null && event.getItemStack().getItem() == theItem && event.getItemStack().getItemDamage() == meta && !world.isRemote) {
                    if (event.getItemStack().getItemUseAction() != EnumAction.EAT || !event.getEntityPlayer().isPotionActive(ModPotions.APPETITE)) {
                        event.setCanceled(true);
                        health.setLastItemName(item);
                        event.getEntityPlayer().setActiveHand(EnumHand.MAIN_HAND);
                    }
                } else health.setLastItemName("none");
            }
        }
        else {
            event.setCanceled(false);
        }
    }
    
    private static IAttributeInstance getAttribute(EntityPlayer player, IAttribute attribute) {
        if (player != null)
            return player.getAttributeMap().getAttributeInstance(attribute);
        return null;
    }
    private static IAttributeInstance getMaxHealthAttribute(EntityPlayer player) {
        return getAttribute(player, SharedMonsterAttributes.MAX_HEALTH);
    }
    private static void removeModifier(EntityPlayer entity, IAttribute attribute, UUID modifierUUID) {
        if (entity != null) {
            AttributeModifier modifier = getAttribute(entity, attribute).getModifier(modifierUUID);
            if (modifier != null) {
                getAttribute(entity, attribute).removeModifier(modifier);
            }
        }
    }
    private static void removeMaxHealthModifier(EntityPlayer entity, UUID modifierUUID) {
        if (entity != null) {
            AttributeModifier modifier = getMaxHealthAttribute(entity).getModifier(modifierUUID);
            if (modifier != null) {
                getMaxHealthAttribute(entity).removeModifier(modifier);
            }
        }
    }
    private static void setMaxHealth(EntityPlayer player, UUID modifierUUID, double health, String name) {
        if (player != null) {
            removeMaxHealthModifier(player, modifierUUID);
            getMaxHealthAttribute(player).applyModifier(new AttributeModifier(modifierUUID, name, health, 0));
            player.setHealth(player.getMaxHealth());
        }
    }
}