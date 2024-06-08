package de.yamayaki.cesium.api.io;

import java.io.IOException;

public interface ISerializer<T> {
    byte[] serialize(T input) throws IOException;

    T deserialize(byte[] input) throws IOException;
}
