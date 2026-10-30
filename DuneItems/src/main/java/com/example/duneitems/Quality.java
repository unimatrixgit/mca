package com.example.duneitems;
public enum Quality {
 COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"), VERY_RARE("Very Rare"), LEGENDARY("Legendary");
 private final String label;
 Quality(String label){this.label=label;}
 public String label(){return label;}
 public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
}
