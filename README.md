# ProxyPass

ProxyPass lets you inspect the packets exchanged between a Minecraft: Bedrock Edition client and server by connecting the client through the proxy.

## Quick start

Install Java 25 and download `ProxyPass.jar` from [GitHub Releases](https://github.com/CloudburstMC/ProxyPass/releases). Run `java -jar ProxyPass.jar` from the directory where you want to keep your configuration and logs. The first launch creates a `config.yml` in that directory, which you can edit after stopping the proxy.

Once you've configured the listener and backend addresses, restart ProxyPass and connect from Minecraft using the listener address. Both the client and backend should match the Bedrock version supported by your ProxyPass build. The current build supports Bedrock 1.26.60 (protocol 2225).

## Configuration

`proxy.host` and `proxy.port` control where ProxyPass listens, while `destination.host` and `destination.port` point to the backend. By default, ProxyPass listens on `0.0.0.0:19122` and connects to `127.0.0.1:19132`. When adding the proxy in Minecraft, use the machine's actual address rather than `0.0.0.0`.

The `transport` setting applies to both connections. It defaults to `nethernet`, but you can set it to `raknet` if that's what your client and backend use. With NetherNet, the listener must bind to a LAN address or `0.0.0.0` rather than a loopback address.

### Packet logs

To record packets, set `log-packets: true` and choose `file`, `console`, or `both` for `log-to`. File captures go to `sessions/<player>-<timestamp>/packets.log` rather than `logs/latest.log`. If a packet is missing from the capture, check whether its name is listed under `ignored-packets`.

If you're checking for codec mismatches, enable `packet-testing: true` to compare incoming packets with their re-encoded versions. Any mismatch warnings appear in the application log.

## Build from source

From the project root with JDK 25:

| Task                             | Linux/macOS           | Windows                   |
| -------------------------------- | --------------------- | ------------------------- |
| Build `build/libs/ProxyPass.jar` | `./gradlew shadowJar` | `.\gradlew.bat shadowJar` |
| Run from source                  | `./gradlew run`       | `.\gradlew.bat run`       |

When running from source, configuration and logs are under `run/`.
