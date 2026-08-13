package org.runite.client;

public class CS2Methods {
    static QuickChat aQuickChat_1056;

    static RSString method27(RSString var0) {
        try {

            int var2 = Unsorted.method1602(var0);
            return var2 != -1 ? Class119.aClass131_1624.aStringArray1721[var2].method1560(RSString.parse(" "), TextCore.aString_4066) : TextCore.aString_4049;
        } catch (RuntimeException var3) {
            throw ClientErrorException.clientError(var3, "rc.V(" + (var0 != null ? "{...}" : "null") + ',' + true + ')');
        }
    }

    static void method28() {
        try {
            Class143.aReferenceCache_1874.clear();

        } catch (RuntimeException var2) {
            throw ClientErrorException.clientError(var2, "rc.Q(" + true + ')');
        }
    }

    static void resetContainer(int containerId) {
        RSContainer container = (RSContainer) TileData.containerTable.get(containerId);
        if (container != null) {

            for (int slot = 0; container.itemIds.length > slot; ++slot) {
                container.itemIds[slot] = -1;
                container.amounts[slot] = 0;
            }

        }
    }

    static void deleteInventory(int var0) {
        try {
            RSContainer var2 = (RSContainer) TileData.containerTable.get(var0);
            if (null != var2) {
                var2.unlink();
            }
        } catch (RuntimeException var3) {
            throw ClientErrorException.clientError(var3, "bc.A(" + var0 + ',' + -28236 + ')');
        }
    }

    static void method2280(int var1) {
        try {

            InterfaceWidget var2 = InterfaceWidget.getWidget(11, var1);
            var2.a();
        } catch (RuntimeException var3) {
            throw ClientErrorException.clientError(var3, "wl.B(" + 2714 + ',' + var1 + ')');
        }
    }

    /* * * * INTERFACE SPECIFIC SEPARATE CORRECTLY * * * */
    static void method225(RSInterface iface) {
        RSInterface var2 = method2273(iface);

        int windowWidth;
        int windowHeight;
        if (var2 == null) {
            windowHeight = GroundItem.canvasHeight;
            windowWidth = Class23.canvasWidth;
        } else {
            windowHeight = var2.height;
            windowWidth = var2.width;
        }

        Unsorted.calculateInterfaceSize(iface, windowWidth, windowHeight, false);
        Unsorted.calculateInterfacePosition(iface, windowWidth, windowHeight);
    }

    //INTERFACE SPECIFIC RENAME
    static RSInterface method2273(RSInterface iface) {
        if (iface.parentId != -1) {
            return Unsorted.getRSInterface(iface.parentId);
        }

        int var3 = iface.componentHash >>> 16;
        Class80<Class3_Sub31> var4 = new Class80<>(TextureOperation23.openInterfaces);

        for (Class3_Sub31 var2 = var4.method1393(); null != var2; var2 = var4.method1392()) {
            if (var2.widgetId == var3) {
                return Unsorted.getRSInterface((int) var2.linkableKey);
            }
        }

        return null;
    }
    /* * * * END INTERFACE * * * */


}
