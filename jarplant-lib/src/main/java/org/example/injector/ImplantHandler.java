package org.example.injector;

import javassist.bytecode.ClassFile;

import java.util.Map;

public interface ImplantHandler {
    ClassName getImplantClassName();

    Map<String, ImplantHandlerImpl.ConfDataType> getAvailableConfig();

    void setConfig(Map<String, Object> bulkConfigs) throws ImplantConfigException;

    void setConfig(String key, Object value) throws ImplantConfigException;

    // Unfortunately, ClassFile is not Cloneable so a fresh instance needs to be read for every injection
    ClassFile loadFreshConfiguredSpecimen();

    ClassFile loadFreshRawSpecimen();

    Map<ClassName, byte[]> getDependencies();
}
