package com.github.gamerbenyt.playy;

import com.github.gamerbenyt.playy.mixin.ClientPlayNetworkHandlerAccessor;
import com.github.gamerbenyt.playy.playing.SongHandler;
import com.github.gamerbenyt.playy.playing.Stage;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public class FakePlayerEntity extends OtherClientPlayerEntity {
	public static final UUID FAKE_PLAYER_UUID = UUID.randomUUID();

	ClientPlayerEntity player = Playy.MC.player;
	ClientWorld world = Playy.MC.world;
	
	public FakePlayerEntity() {
		super(Playy.MC.world, getProfile());
		
		copyStagePosAndPlayerLook();
		
		getInventory().clone(player.getInventory());
		
		Byte playerModel = player.getDataTracker().get(PlayerEntity.PLAYER_MODE_CUSTOMIZATION_ID);
		getDataTracker().set(PlayerEntity.PLAYER_MODE_CUSTOMIZATION_ID, playerModel);
		
		headYaw = player.headYaw;
		bodyYaw = player.bodyYaw;

		if (player.isSneaking()) {
			setSneaking(true);
			setPose(EntityPose.CROUCHING);
		}

//		capeX = getX();
//		capeY = getY();
//		capeZ = getZ();
		
		world.addEntity(this);
	}
	
	public void resetPlayerPosition() {
		player.refreshPositionAndAngles(getX(), getY(), getZ(), getYaw(), getPitch());
	}
	
	public void copyStagePosAndPlayerLook() {
		Stage lastStage = SongHandler.getInstance().lastStage;
		if (lastStage != null) {
			refreshPositionAndAngles(lastStage.position.getX()+0.5, lastStage.position.getY(), lastStage.position.getZ()+0.5, player.getYaw(), player.getPitch());
			headYaw = player.headYaw;
		}
		else {
			copyPositionAndRotation(player);
		}
	}

	private static GameProfile getProfile() {
		GameProfile profile = new GameProfile(
				FAKE_PLAYER_UUID,
				Playy.MC.player.getGameProfile().name(),
				Playy.MC.getGameProfile().properties()
		);
		PlayerListEntry playerListEntry = new PlayerListEntry(Playy.MC.player.getGameProfile(), false);
		((ClientPlayNetworkHandlerAccessor)Playy.MC.getNetworkHandler()).getPlayerListEntries().put(FAKE_PLAYER_UUID, playerListEntry);
		return profile;
	}
}
