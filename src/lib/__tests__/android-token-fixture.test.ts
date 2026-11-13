import { readFileSync, writeFileSync } from "node:fs";
import crypto from "node:crypto";
import { expect, it } from "vitest";
import { DEVICE_TOKEN_INFO, encryptForDevice, STATUS_LIGHT_INFO } from "../crypto";

it.each([
  ["token-envelope.json", "public-interoperability-test", DEVICE_TOKEN_INFO],
  ["status-light-envelope.json", "public-status-light-test", STATUS_LIGHT_INFO],
])(
  "provides Android decryption fixture %s from the real server encryptor",
  (file, expected, info) => {
    // Published RFC 7748 section 6.1 test scalar; never a device credential.
    const scalar = Buffer.from(
      "77076d0a7318a57d3c16c17251b26645df" + "4c2f87ebc0992ab177fba51db92c2a",
      "hex"
    );
    const privateKey = crypto.createPrivateKey({
      key: Buffer.concat([Buffer.from("302e020100300506032b656e04220420", "hex"), scalar]),
      format: "der",
      type: "pkcs8",
    });
    const publicKey = crypto
      .createPublicKey(privateKey)
      .export({ type: "spki", format: "der" })
      .subarray(-32)
      .toString("base64");
    const path = `android/app/src/test/resources/${file}`;
    if (process.env.UPDATE_ANDROID_CRYPTO_FIXTURE === "1") {
      writeFileSync(
        path,
        JSON.stringify(
          {
            expected,
            ...encryptForDevice(expected, publicKey, info),
          },
          null,
          2
        ) + "\n"
      );
    }
    const fixture = JSON.parse(readFileSync(path, "utf8"));
    const peer = crypto.createPublicKey({
      key: Buffer.concat([
        Buffer.from("302a300506032b656e032100", "hex"),
        Buffer.from(fixture.serverPublicKey, "base64"),
      ]),
      format: "der",
      type: "spki",
    });
    const shared = crypto.diffieHellman({ privateKey, publicKey: peer });
    const key = Buffer.from(
      crypto.hkdfSync("sha256", shared, Buffer.alloc(0), Buffer.from(info), 32)
    );
    const encrypted = Buffer.from(fixture.ciphertext, "base64");
    const decipher = crypto.createDecipheriv(
      "aes-256-gcm",
      key,
      Buffer.from(fixture.nonce, "base64")
    );
    decipher.setAuthTag(encrypted.subarray(-16));
    expect(
      Buffer.concat([decipher.update(encrypted.subarray(0, -16)), decipher.final()]).toString()
    ).toBe(fixture.expected);
    expect(fixture.expected).toBe(expected);
  }
);
