/**
 * Copyright 2010, 2011 Marc Giger
 *
 * This file is part of the streaming-webservice-security-framework (swssf).
 *
 * The streaming-webservice-security-framework is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The streaming-webservice-security-framework is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the streaming-webservice-security-framework.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swssf.test;

import org.swssf.impl.util.TrimmerOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;

/**
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class TrimmerOutputStreamTest {

    private final String testString = "Within this class we test if the TrimmerOutputStream works correctly under different conditions";

    @Test
    public void testWriteSingleBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TrimmerOutputStream trimmerOutputStream = new TrimmerOutputStream(baos, 32, 3, 4);

        byte[] testStringBytes = ("<a>" + testString + "</a>").getBytes();
        for (int i = 0; i < testStringBytes.length; i++) {
            trimmerOutputStream.write(testStringBytes[i]);
        }
        trimmerOutputStream.close();

        Assert.assertEquals(baos.size(), testStringBytes.length - 7);
        Assert.assertEquals(baos.toString(), testString);
    }

    @Test
    public void testWriteRandomByteSizes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TrimmerOutputStream trimmerOutputStream = new TrimmerOutputStream(baos, 32, 3, 4);

        StringBuilder stringBuffer = new StringBuilder("<a>");
        for (int i = 0; i < 100; i++) {
            stringBuffer.append(testString);
        }
        stringBuffer.append("</a>");

        byte[] testStringBytes = stringBuffer.toString().getBytes();

        int written = 0;
        int count = 0;
        do {
            count++;
            trimmerOutputStream.write(testStringBytes, written, count);
            written += count;
        }
        while ((written + count + 1) < testStringBytes.length);

        trimmerOutputStream.write(testStringBytes, written, testStringBytes.length - written);
        trimmerOutputStream.close();

        Assert.assertEquals(baos.size(), testStringBytes.length - 7);
        Assert.assertEquals(baos.toString(), stringBuffer.toString().substring(3, stringBuffer.length() - 4));
    }
}
