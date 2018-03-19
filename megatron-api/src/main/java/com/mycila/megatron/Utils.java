/*
 * Copyright © 2017 Mathieu Carbou (mathieu.carbou@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycila.megatron;

import com.tc.classloader.CommonComponent;

import javax.xml.bind.DatatypeConverter;
import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class Utils {

  public static void closeSilently(Closeable... closeables) {
    for (Closeable closeable : closeables) {
      try {
        closeable.close();
      } catch (IOException ignored) {
      }
    }
  }

  public static String generateShortUUID() {
    UUID j = UUID.randomUUID();
    byte[] data = new byte[16];
    long msb = j.getMostSignificantBits();
    long lsb = j.getLeastSignificantBits();
    for (int i = 0; i < 8; i++) {
      data[i] = (byte) (msb & 0xff);
      msb >>>= 8;
    }
    for (int i = 8; i < 16; i++) {
      data[i] = (byte) (lsb & 0xff);
      lsb >>>= 8;
    }
    return DatatypeConverter.printBase64Binary(data)
        // java-8 and otehr - compatible B64 url decoder using - and _ instead of + and /
        // padding can be ignored to shorter the UUID
        .replace('+', '-').replace('/', '_').replace("=", "");
  }

}
