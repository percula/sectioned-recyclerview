package com.afollestad.sectionedrecyclerview;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.ViewGroup;

import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class SectionedRecyclerViewAdapter<VH extends SectionedViewHolder>
        extends RecyclerView.Adapter<VH> implements ItemProvider {

    private static final String TAG = "SectionedRVAdapter";

    protected static final int VIEW_TYPE_HEADER = -2;
    protected static final int VIEW_TYPE_ITEM = -1;

    private PositionManager positionManager;
    private GridLayoutManager layoutManager;
    private boolean showHeadersForEmptySections;

    public SectionedRecyclerViewAdapter() {
        positionManager = new PositionManager();
    }

    /**
     * Notifies the adapter when a section is removed (swiped or dismissed)
     *
     * @param section Section to remove
     */
    public void notifySectionRemoved(@IntRange(from = 0, to = Integer.MAX_VALUE) int section) {
        int[] sectionRange = getSectionRange(section);
        if (sectionRange == null) {
            return;
        }
        int startPosition = sectionRange[0];
        int sectionItemCount = sectionRange[1];

        // Adjust expand/collapse mapping
        for (int i = section; i < getSectionCount(); i++) {
            if (positionManager.isSectionExpanded(section + 1)) {
                positionManager.expandSection(section);
            } else {
                positionManager.collapseSection(section);
            }
        }

        notifyItemRangeRemoved(startPosition - 1, sectionItemCount + 1);

    }

    /**
     * Notifies the adapter when a section is inserted
     *
     * @param section Section to insert
     */
    public void notifySectionInserted(@IntRange(from = 0, to = Integer.MAX_VALUE) int section) {
        int[] sectionRange = getSectionRange(section);
        if (sectionRange == null) {
            return;
        }
        int startPosition = sectionRange[0];
        int sectionItemCount = sectionRange[1];

        // Adjust expand/collapse mapping
        for (int i = section; i < getSectionCount() - 1; i++) {
            if (positionManager.isSectionExpanded(i)) {
                positionManager.expandSection(i + 1);
            } else {
                positionManager.collapseSection(i + 1);
            }
        }
        
        notifyItemRangeInserted(startPosition - 1, sectionItemCount + 1);
    }

    /**
     * Notifies the adapter when a section is expanded
     * @param section  Section to expand/collapse
     */
    public void notifySectionExpanded(@IntRange(from = 0, to = Integer.MAX_VALUE) int section) {
        int[] sectionRange = getSectionRange(section);
        if (sectionRange == null) {
            return;
        }
        int startPosition = sectionRange[0];
        int sectionItemCount = sectionRange[1];
        if (sectionItemCount == 0) {
            Log.d(TAG, "There are no items in section " + section + " to notify.");
            return;
        }
        notifyItemRangeInserted(startPosition, sectionItemCount);
    }

    /**
     * Notifies the adapter when a section is collapsed
     *
     * @param section Section to expand/collapse
     */
    public void notifySectionCollapsed(@IntRange(from = 0, to = Integer.MAX_VALUE) int section) {
        int[] sectionRange = getSectionRange(section);
        if (sectionRange == null) {
            return;
        }
        int startPosition = sectionRange[0];
        int sectionItemCount = sectionRange[1];
        if (sectionItemCount == 0) {
            Log.d(TAG, "There are no items in section " + section + " to notify.");
            return;
        }
        notifyItemRangeRemoved(startPosition, sectionItemCount);
    }

    /**
     * Helper function to determine the range of items within a section
     *
     * @param section Section to get range of items for
     * @return Integer array containing:
     * 1) The starting position of the section's items (not including the section header)
     * 2) The number of items within the section.
     */
    private int[] getSectionRange(@IntRange(from = 0, to = Integer.MAX_VALUE) int section) {
        if (section < 0 || section > getSectionCount() - 1) {
            throw new IllegalArgumentException(
                    "Section " + section + " is out of range of existing sections.");
        }
        Integer sectionHeaderIndex = positionManager.sectionHeaderIndex(section);
        if (sectionHeaderIndex == -1) {
            throw new IllegalStateException("No header position mapped for section " + section);
        }
        int sectionItemCount = getItemCount(section);
        int startPosition = sectionHeaderIndex + 1;
        Log.d(TAG, "Invalidating " + sectionItemCount + " items starting at index " + startPosition);
        return new int[]{startPosition, sectionItemCount};
    }

    public void expandSection(int section) {
        positionManager.expandSection(section);
        notifyDataSetChanged();
    }

    public void collapseSection(int section) {
        positionManager.collapseSection(section);
        notifyDataSetChanged();
    }

    public void toggleSectionExpanded(int section) {
        positionManager.toggleSectionExpanded(section);
        if (positionManager.isSectionExpanded(section)) {
            notifySectionExpanded(section);
        } else {
            notifySectionCollapsed(section);
        }
        notifyItemChanged(getSectionHeaderIndex(section));
    }


    public abstract int getSectionCount();

    public abstract int getItemCount(int section);

    public abstract void onBindHeaderViewHolder(VH holder, int section, boolean expanded);

    public abstract void onBindViewHolder(
            VH holder, int section, int relativePosition, int absolutePosition);

    public final boolean isHeader(int position) {
        return positionManager.isHeader(position);
    }

    public final boolean isSectionExpanded(int section) {
        return positionManager.isSectionExpanded(section);
    }

    public final int getSectionHeaderIndex(int section) {
        return positionManager.sectionHeaderIndex(section);
    }

    public final void shouldShowHeadersForEmptySections(boolean show) {
        showHeadersForEmptySections = show;
    }

    public final void setLayoutManager(@Nullable GridLayoutManager lm) {
        layoutManager = lm;
        if (lm == null) {
            return;
        }
        lm.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        if (isHeader(position)) {
                            return layoutManager.getSpanCount();
                        }
                        ItemCoord sectionAndPos = getRelativePosition(position);
                        int absPos = position - (sectionAndPos.section() + 1);
                        return getRowSpan(
                                layoutManager.getSpanCount(),
                                sectionAndPos.section(),
                                sectionAndPos.relativePos(),
                                absPos);
                    }
                });
    }

    protected int getRowSpan(
            int fullSpanSize, int section, int relativePosition, int absolutePosition) {
        return 1;
    }

    /**
     * Converts an absolute position to a relative position and section.
     */
    public ItemCoord getRelativePosition(int absolutePosition) {
        return positionManager.relativePosition(absolutePosition);
    }

    /**
     * Converts a relative position (index inside of a section) to an absolute position (index out of
     * all items and headers).
     */
    public int getAbsolutePosition(int sectionIndex, int relativeIndex) {
        return positionManager.absolutePosition(sectionIndex, relativeIndex);
    }

    /**
     * Converts a relative position (index inside of a section) to an absolute position (index out of
     * all items and headers).
     */
    public int getAbsolutePosition(ItemCoord relativePosition) {
        return positionManager.absolutePosition(relativePosition);
    }

    @Override
    public final int getItemCount() {
        return positionManager.invalidate(this);
    }

    @Override
    public final boolean showHeadersForEmptySections() {
        return showHeadersForEmptySections;
    }

    /**
     * @hide
     * @deprecated
     */
    @Override
    @Deprecated
    public long getItemId(int position) {
        if (isHeader(position)) {
            int pos = positionManager.sectionId(position);
            return getHeaderId(pos);
        } else {
            ItemCoord sectionAndPos = getRelativePosition(position);
            return getItemId(sectionAndPos.section(), sectionAndPos.relativePos());
        }
    }

    public long getHeaderId(int section) {
        return super.getItemId(section);
    }

    public long getItemId(int section, int position) {
        return super.getItemId(position);
    }

    /**
     * @hide
     * @deprecated
     */
    @Override
    @Deprecated
    public final int getItemViewType(int position) {
        if (isHeader(position)) {
            return getHeaderViewType(positionManager.sectionId(position));
        } else {
            ItemCoord sectionAndPos = getRelativePosition(position);
            return getItemViewType(
                    sectionAndPos.section(),
                    // offset section view positions
                    sectionAndPos.relativePos(),
                    position - (sectionAndPos.section() + 1));
        }
    }

    @IntRange(from = 0, to = Integer.MAX_VALUE)
    public int getHeaderViewType(int section) {
        //noinspection ResourceType
        return VIEW_TYPE_HEADER;
    }

    @IntRange(from = 0, to = Integer.MAX_VALUE)
    public int getItemViewType(int section, int relativePosition, int absolutePosition) {
        //noinspection ResourceType
        return VIEW_TYPE_ITEM;
    }

    /**
     * @hide
     * @deprecated
     */
    @Override
    @Deprecated
    public final void onBindViewHolder(VH holder, int position) {
        holder.setPositionDelegate(positionManager);

        StaggeredGridLayoutManager.LayoutParams layoutParams = null;
        if (holder.itemView.getLayoutParams() instanceof GridLayoutManager.LayoutParams)
            layoutParams =
                    new StaggeredGridLayoutManager.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        else if (holder.itemView.getLayoutParams() instanceof StaggeredGridLayoutManager.LayoutParams) {
            layoutParams = (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
        }

        if (isHeader(position)) {
            if (layoutParams != null) {
                layoutParams.setFullSpan(true);
            }
            int sectionIndex = positionManager.sectionId(position);
            onBindHeaderViewHolder(holder, sectionIndex, isSectionExpanded(sectionIndex));
        } else {
            if (layoutParams != null) {
                layoutParams.setFullSpan(false);
            }
            ItemCoord sectionAndPos = getRelativePosition(position);
            int absPos = position - (sectionAndPos.section() + 1);
            onBindViewHolder(
                    holder,
                    sectionAndPos.section(),
                    // offset section view positions
                    sectionAndPos.relativePos(),
                    absPos);
        }

        if (layoutParams != null) {
            holder.itemView.setLayoutParams(layoutParams);
        }
    }

    /**
     * @hide
     * @deprecated
     */
    @Deprecated
    @Override
    public final void onBindViewHolder(VH holder, int position, List<Object> payloads) {
        super.onBindViewHolder(holder, position, payloads);
    }
}
