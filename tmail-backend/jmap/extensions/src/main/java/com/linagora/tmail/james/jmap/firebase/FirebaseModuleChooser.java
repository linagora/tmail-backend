package com.linagora.tmail.james.jmap.firebase;

import java.util.List;

import com.google.inject.Module;

public class FirebaseModuleChooser {
    public static List<Module> chooseFirebase(FirebaseModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new FirebaseModule());
        }
        return List.of();
    }
}
