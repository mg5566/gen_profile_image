package com.example.genprofileimage.profile;

import java.io.IOException;
import java.nio.file.Path;

public interface ComfyUiClient {

    byte[] generateSuitProfile(Path inputImagePath) throws IOException;
}
