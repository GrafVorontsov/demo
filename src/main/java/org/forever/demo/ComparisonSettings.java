package org.forever.demo;
public class ComparisonSettings {
    private boolean compareByAbsoluteValue;
    private boolean comparePrihodRashod;

    public ComparisonSettings(boolean compareByAbsoluteValue, boolean comparePrihodRashod) {
        this.compareByAbsoluteValue = compareByAbsoluteValue;
        this.comparePrihodRashod = comparePrihodRashod;
    }

    public boolean isCompareByAbsoluteValue() {
        return compareByAbsoluteValue;
    }

    public void setCompareByAbsoluteValue(boolean compareByAbsoluteValue) {
        this.compareByAbsoluteValue = compareByAbsoluteValue;
    }

    public boolean isComparePrihodRashod() {
        return comparePrihodRashod;
    }

    public void setComparePrihodRashod(boolean comparePrihodRashod) {
        this.comparePrihodRashod = comparePrihodRashod;
    }
}