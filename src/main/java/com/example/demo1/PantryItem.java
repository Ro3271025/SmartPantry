//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.demo1;

import java.time.LocalDate;

public class PantryItem {
    private final String name;
    private final String quantityLabel;
    private final LocalDate expires;
    private final int quantityNumeric;

    public PantryItem(String name, String quantityLabel, int quantityNumeric, LocalDate expires) {
        this.name = name;
        this.quantityLabel = quantityLabel;
        this.quantityNumeric = quantityNumeric;
        this.expires = expires;
    }


    public String getName() {
        return this.name;
    }

    public String getQuantityLabel() {
        return this.quantityLabel;
    }

    public LocalDate getExpires() {
        return this.expires;
    }

    public int getQuantityNumeric() {
        return this.quantityNumeric;
    }
}
