package org.example.injector;

import javassist.bytecode.ClassFile;

import java.io.IOException;
import java.util.Map;

public class ImplantHandlerMock implements ImplantHandler {
    private final ClassFile specimen;

    ImplantHandlerMock(ClassFile specimen) {
        this.specimen = specimen;
    }

    @Override
    public String getImplantClassName() {
        return specimen.getName();
    }

    @Override
    public Map<String, ImplantHandlerImpl.ConfDataType> getAvailableConfig() {
        return Map.of();
    }

    @Override
    public void setConfig(Map<String, Object> bulkConfigs) throws ImplantConfigException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConfig(String key, Object value) throws ImplantConfigException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassFile loadFreshConfiguredSpecimen() throws IOException {
        // WARNING: Returning mutable reference
        return specimen;
    }

    @Override
    public ClassFile loadFreshRawSpecimen() throws IOException {
        // WARNING: Returning mutable reference
        return specimen;
    }
}
