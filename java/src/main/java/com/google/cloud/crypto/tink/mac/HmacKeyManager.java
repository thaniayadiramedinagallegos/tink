// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.cloud.crypto.tink.mac;

import com.google.cloud.crypto.tink.CommonProto.HashType;
import com.google.cloud.crypto.tink.HmacProto.HmacKey;
import com.google.cloud.crypto.tink.HmacProto.HmacKeyFormat;
import com.google.cloud.crypto.tink.HmacProto.HmacParams;
import com.google.cloud.crypto.tink.KeyManager;
import com.google.cloud.crypto.tink.Mac;
import com.google.cloud.crypto.tink.TinkProto.KeyFormat;
import com.google.cloud.crypto.tink.subtle.MacJce;
import com.google.cloud.crypto.tink.subtle.Random;
import com.google.cloud.crypto.tink.subtle.SubtleUtil;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.GeneralSecurityException;
import javax.crypto.spec.SecretKeySpec;

final class HmacKeyManager implements KeyManager<Mac> {
  /**
   * Type url that this manager does support.
   */
  static final String TYPE_URL = "type.googleapis.com/google.cloud.crypto.tink.HmacKey";
  /**
   * Current version of this key manager.
   * Keys with version equal or smaller are supported.
   */
  private static final int VERSION = 0;

  /**
   * Minimum key size in bytes.
   */
  private static final int MIN_KEY_SIZE_IN_BYTES = 16;

  /**
   * Minimum tag size in bytes. This provides minimum 80-bit security strength.
   */
  private static final int MIN_TAG_SIZE_IN_BYTES = 10;

  @Override
  public Mac getPrimitive(Any proto) throws GeneralSecurityException {
    try {
      HmacKey hmac = proto.unpack(HmacKey.class);
      validate(hmac);
      HashType hash = hmac.getParams().getHash();
      byte[] keyValue = hmac.getKeyValue().toByteArray();
      SecretKeySpec keySpec = new SecretKeySpec(keyValue, "HMAC");
      int tagSize = hmac.getParams().getTagSize();
      switch (hash) {
        case SHA1 : return new MacJce("HMACSHA1", keySpec, tagSize);
        case SHA256 : return new MacJce("HMACSHA256", keySpec, tagSize);
        case SHA512 : return new MacJce("HMACSHA512", keySpec, tagSize);
        default: throw new GeneralSecurityException("Unknown hash");
      }
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("invalid Hmac key");
    }
  }

  @Override
  public Any newKey(KeyFormat keyFormat) throws GeneralSecurityException {
    try {
      HmacKeyFormat format = keyFormat.getFormat().unpack(HmacKeyFormat.class);
      validate(format);
      return Any.pack(HmacKey.newBuilder()
          .setVersion(VERSION)
          .setParams(format.getParams())
          .setKeyValue(ByteString.copyFrom(Random.randBytes(format.getKeySize())))
          .build());
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("cannot generate Hmac key");
    }
  }

  @Override
  public boolean doesSupport(String typeUrl) {
    return typeUrl.equals(TYPE_URL);
  }

  private void validate(HmacKey key) throws GeneralSecurityException {
    SubtleUtil.validateVersion(key.getVersion(), VERSION);
    if (key.getKeyValue().size() < MIN_KEY_SIZE_IN_BYTES) {
      throw new GeneralSecurityException("key too short");
    }
    validate(key.getParams());
  }

  private void validate(HmacKeyFormat format) throws GeneralSecurityException {
    if (format.getKeySize() < MIN_KEY_SIZE_IN_BYTES) {
      throw new GeneralSecurityException("key too short");
    }
    validate(format.getParams());

  }

  private void validate(HmacParams params) throws GeneralSecurityException {
    if (params.getTagSize() < MIN_TAG_SIZE_IN_BYTES) {
      throw new GeneralSecurityException("tag size too small");
    }
    switch (params.getHash()) {
      case SHA1:
        if (params.getTagSize() > 20) {
          throw new GeneralSecurityException("tag size too big");
        }
        break;
      case SHA256:
        if (params.getTagSize() > 32) {
          throw new GeneralSecurityException("tag size too big");
        }
        break;
      case SHA512:
        if (params.getTagSize() > 64) {
          throw new GeneralSecurityException("tag size too big");
        }
        break;
      default:
        throw new GeneralSecurityException("unknown hash type");
    }
  }
}
