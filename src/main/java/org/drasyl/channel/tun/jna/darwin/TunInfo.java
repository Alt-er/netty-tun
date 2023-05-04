package org.drasyl.channel.tun.jna.darwin;


import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;



public class TunInfo extends Structure {
    public int fd;
    public int mtu;
    public byte[] device_name = new byte[16];

    public TunInfo() {

    }
//    public static class ByReference extends TunInfo implements Structure.ByReference{ }
//
//    public static class ByValue extends TunInfo implements Structure.ByValue{ }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("fd", "mtu", "device_name");
    }
}
