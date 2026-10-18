package com.relay.spectate;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A spectator client is never told what is in another player's inventory, so item counts come
 * from this mod running on the server: the client asks, the server answers with a count per item.
 */
public final class RelayNet {
	private RelayNet() {}

	/** Client to server: "send me the inventories of these two players". */
	public record Request(String a, String b) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relay_spectator", "request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Request::a,
			ByteBufCodecs.STRING_UTF8, Request::b,
			Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Server to client: item counts for one player. Keys are item paths plus potion_healing / potion_other. */
	public record Summary(String name, Map<String, Integer> counts) implements CustomPacketPayload {
		public static final Type<Summary> TYPE = new Type<>(Identifier.fromNamespaceAndPath("relay_spectator", "summary"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Summary> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Summary::name,
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), Summary::counts,
			Summary::new);

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Must run on both sides, before any connection. */
	public static void registerTypes() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Summary.TYPE, Summary.CODEC);
	}
}
