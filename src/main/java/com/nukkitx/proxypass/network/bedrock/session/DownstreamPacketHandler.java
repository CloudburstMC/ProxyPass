package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.nbt.stream.NetworkDataOutputStream;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.session.BedrockSession;
import com.nukkitx.protocol.bedrock.v332.Bedrock_v332;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.EncryptionUtils;
import io.netty.buffer.ByteBuf;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class DownstreamPacketHandler implements BedrockPacketHandler {
    private final BedrockSession<ProxyPlayerSession> session;
    private final ProxyPass proxy;

    public boolean handle(ServerToClientHandshakePacket packet) {
        try {
            SignedJWT saltJwt = SignedJWT.parse(packet.getJwt());
            URI x5u = saltJwt.getHeader().getX509CertURL();
            ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
            byte[] encryptionKey = EncryptionUtils.getServerKey(session.getPlayer().getProxyKeyPair(), serverKey,
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt")));
            session.enableEncryption(new SecretKeySpec(encryptionKey, "AES"));
        } catch (ParseException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        session.sendPacketImmediately(clientToServerHandshake);
        return true;
    }

    public boolean handle(AvailableEntityIdentifiersPacket packet) {
        proxy.saveData("entity_identifiers", packet.getTag());
        return false;
    }

    public boolean handle(BiomeDefinitionListPacket packet) {
        proxy.saveData("biome_definitions", packet.getTag());
        return false;
    }

    public boolean handle(StartGamePacket packet) {
        Path legacyIdMapPath = proxy.getDataDir().resolve("legacy_ids.json");
        if (Files.notExists(legacyIdMapPath) || !Files.isRegularFile(legacyIdMapPath)) {
            return false;
        }

        Map<String, Integer> map;
        try {
            map = ProxyPass.JSON_MAPPER.readValue(Files.newInputStream(legacyIdMapPath), new TypeReference<Map<String, Integer>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException();
        }

        List<Entry> table = new ArrayList<>();
        for (StartGamePacket.PaletteEntry entry : packet.getPaletteEntries()) {
            String name = entry.getIdentifier();
            table.add(new Entry(name, map.get(name), entry.getMeta()));
        }
        Collections.sort(table);

        Path outPath = proxy.getDataDir().resolve("runtimeid_table.json");
        try {
            OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            ProxyPass.JSON_MAPPER.writeValue(outputStream, table);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    @Value
    private static class Entry implements Comparable<Entry> {
        private final String name;
        private final int id;
        private final int data;

        @Override
        public int compareTo(@Nonnull Entry that) {
            int comparison = Integer.compare(this.id, that.id);
            if (comparison == 0) {
                comparison = Integer.compare(this.data, that.data);
            }
            return comparison;
        }
    }
}
