/*
 * TestApi.java created by Minki Moon(mooner1022) on 1/20/23, 8:59 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.api.original;

import androidx.annotation.NonNull;
import dev.mooner.starlight.plugincore.api.Api;
import dev.mooner.starlight.plugincore.api.ApiObject;
import dev.mooner.starlight.plugincore.api.InstanceType;
import dev.mooner.starlight.plugincore.project.Project;

import java.util.ArrayList;
import java.util.List;

public class TestApi extends Api<TestApi.Test> {

    @NonNull
    @Override
    public String getName() {
        return "Test";
    }

    @NonNull
    @Override
    public List<ApiObject> getObjects() {
        return new ArrayList<>();
    }

    @NonNull
    @Override
    public Class<Test> getInstanceClass() {
        return Test.class;
    }

    @NonNull
    @Override
    public InstanceType getInstanceType() {
        return InstanceType.CLASS;
    }

    @NonNull
    @Override
    public Object getInstance(@NonNull Project project) {
        return Test.class;
    }

    public static class Test {

        public static void test(VarargCallback callback) {
            callback.run(1, 2, 3);
        }
    }

    public interface VarargCallback {

        void run(Object ...args);
    }
}
