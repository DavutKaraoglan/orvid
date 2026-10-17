package com.orvid.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.orvid.R;
import com.orvid.app.OrvidApp;
import com.orvid.yt.Visionos;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.List;

final class VideoAdapter extends BaseAdapter {

    static final int CARD = 0;
    static final int COMPACT = 1;
    static final int GRID = 2;
    static final int TEXT = 3;
    static final int GRID3 = 4;

    private static final long PREPARE_DELAY_MS = 120;

    private static float slop(View v) {
        return android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
    }

    static int columns(int mode) {
        return mode == GRID ? 2 : mode == GRID3 ? 3 : 1;
    }

    private static final int[] CELLS_2 = {R.id.cell_left, R.id.cell_right};
    private static final int[] CELLS_3 = {R.id.cell_left, R.id.cell_mid, R.id.cell_right};

    interface OnPick {
        void pick(StreamInfoItem item);
    }

    private final Context context;
    private final OnPick onPick;
    private final List<StreamInfoItem> items = new ArrayList<>();
    private final int screenWidth;
    private int mode;
    private boolean idle = true;
    private com.orvid.search.Query query;

    private Previewer previewer;
    private boolean previewing;

    void setPreviewer(Previewer previewer) {
        this.previewer = previewer;
    }

    void setQuery(com.orvid.search.Query query) {
        this.query = query;
    }

    void setIdle(boolean idle) {
        this.idle = idle;
    }

    VideoAdapter(Context context, int mode, OnPick onPick) {
        this.context = context;
        this.mode = mode;
        this.onPick = onPick;
        screenWidth = context.getResources().getDisplayMetrics().widthPixels;
    }

    void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        notifyDataSetChanged();
    }

    private int skeletons;

    void setSkeleton(int count) {
        items.clear();
        skeletons = count;
        notifyDataSetChanged();
    }

    void set(List<StreamInfoItem> next) {
        skeletons = 0;
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
        prefetch();
    }

    private static final int PREFETCH = 80;

    private void prefetch() {
        if (mode == TEXT) return;
        int cols = columns(mode);
        int width = cols > 1 ? screenWidth / cols : mode == COMPACT ? Math.min(360, screenWidth / 2) : Math.min(640, screenWidth);
        com.orvid.image.Images images = OrvidApp.get().images();
        for (int i = 0; i < Math.min(items.size(), PREFETCH); i++) {
            StreamInfoItem item = items.get(i);
            images.prefetch(Ui.thumbnail(item.getThumbnails(), width), width, item.getUrl());
        }
    }

    void add(List<StreamInfoItem> more) {
        if (skeletons > 0) {
            skeletons = 0;
            items.clear();
        }
        items.addAll(more);
        notifyDataSetChanged();
        prefetch();
    }

    int itemCount() {
        return items.size();
    }

    List<StreamInfoItem> items() {
        return new ArrayList<>(items);
    }

    int columns() {
        return columns(mode);
    }

    @Override
    public int getCount() {
        int n = skeletons > 0 && items.isEmpty() ? skeletons : items.size();
        int cols = columns(mode);
        return (n + cols - 1) / cols;
    }

    @Override
    public Object getItem(int position) {
        return columns(mode) > 1 ? null : items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    @Override
    public int getItemViewType(int position) {
        return mode;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) view = LayoutInflater.from(context).inflate(layout(), parent, false);
        int cols = columns(mode);
        int[] cells = cols == 3 ? CELLS_3 : CELLS_2;
        if (skeletons > 0 && items.isEmpty()) {
            if (cols > 1) {
                for (int id : cells) {
                    View cell = view.findViewById(id);
                    skeleton(cell);
                    cell.setVisibility(View.VISIBLE);
                }
            } else {
                skeleton(view);
            }
            return view;
        }
        if (cols > 1) {
            int first = position * cols;
            for (int i = 0; i < cols; i++) {
                View cell = view.findViewById(cells[i]);
                if (first + i < items.size()) {
                    cell.setVisibility(View.VISIBLE);
                    bindCell(cell, items.get(first + i), screenWidth / cols);
                } else {
                    cell.setVisibility(View.INVISIBLE);
                    cell.setOnClickListener(null);
                }
            }
            return view;
        }
        int thumbWidth = mode == COMPACT ? Math.min(360, screenWidth / 2) : Math.min(640, screenWidth);
        bindCell(view, items.get(position), thumbWidth);
        return view;
    }

    private int layout() {
        switch (mode) {
            case COMPACT: return R.layout.row_compact;
            case GRID: return R.layout.row_grid;
            case GRID3: return R.layout.row_grid3;
            case TEXT: return R.layout.row_text;
            default: return R.layout.row_video;
        }
    }

    private CharSequence highlighted(String title) {
        if (title == null || query == null) return title;
        java.util.List<int[]> spans = com.orvid.search.Ranker.highlights(query, title);
        if (spans.isEmpty()) return title;
        android.text.SpannableString out = new android.text.SpannableString(title);
        for (int[] s : spans) {
            out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s[0], s[1],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private void skeleton(View view) {
        TextView name = view.findViewById(R.id.name);
        TextView sub = view.findViewById(R.id.sub);
        ThumbView thumb = view.findViewById(R.id.thumb);
        view.findViewById(R.id.duration).setVisibility(View.GONE);
        View badge = view.findViewById(R.id.source_icon);
        if (badge != null) badge.setVisibility(View.GONE);
        if (thumb != null) {
            thumb.setTag(R.id.image_key, null);
            thumb.setImageDrawable(null);
        }
        if (name != null) {
            name.setText("　");
            skeletonBar(name, new android.graphics.drawable.InsetDrawable(
                    context.getDrawable(R.drawable.bg_skeleton), 0, Ui.dp(context, 3), Ui.dp(context, 24), Ui.dp(context, 3)));
        }
        View sub2 = view.findViewById(R.id.sub2);
        if (sub2 != null) sub2.setVisibility(View.GONE);
        if (sub != null) {
            sub.setText("　");
            skeletonBar(sub, new android.graphics.drawable.InsetDrawable(
                    context.getDrawable(R.drawable.bg_skeleton), 0, Ui.dp(context, 3), Ui.dp(context, 120), Ui.dp(context, 3)));
        }
        view.setOnClickListener(null);
        view.setOnTouchListener(null);
        view.setClickable(false);
        if (view.getTag(R.id.skeleton_anim) == null) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f);
            pulse.setDuration(750);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.start();
            view.setTag(R.id.skeleton_anim, pulse);
        }
    }

    private static void skeletonBar(View v, android.graphics.drawable.Drawable bar) {
        int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackground(bar);
        v.setPadding(l, t, r, b);
    }

    private static void unskeleton(View view, TextView name, TextView sub) {
        Object running = view.getTag(R.id.skeleton_anim);
        if (running != null) {
            ((android.animation.ObjectAnimator) running).cancel();
            view.setTag(R.id.skeleton_anim, null);
            view.setAlpha(1f);
        }
        if (name != null) name.setBackground(null);
        if (sub != null) sub.setBackground(null);
    }

    private static int sourceIcon(int service) {
        if (service == com.orvid.dm.Dailymotion.SERVICE_ID) return R.drawable.ic_src_dailymotion;
        if (service == com.orvid.vk.Vk.SERVICE_ID) return R.drawable.ic_src_vk;
        com.orvid.search.Source extra = Sources.byService(service);
        if (extra != null) return extra.icon;
        return R.drawable.ic_src_youtube;
    }

    private void bindCell(View view, StreamInfoItem item, int thumbWidth) {
        TextView name = view.findViewById(R.id.name);
        TextView sub = view.findViewById(R.id.sub);
        TextView duration = view.findViewById(R.id.duration);
        ThumbView thumb = view.findViewById(R.id.thumb);

        unskeleton(view, name, sub);
        if (name != null) name.setText(highlighted(item.getName()));
        String views = item.getViewCount() >= 0
                ? context.getString(R.string.views, Ui.count(item.getViewCount())) : "";
        String date = item.getTextualUploadDate();
        if (item.getUploadDate() != null) {
            date = android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.getUploadDate().getInstant().toEpochMilli()).toString();
        }
        String source = item.getServiceId() == com.orvid.dm.Dailymotion.SERVICE_ID
                ? context.getString(R.string.source_dailymotion)
                : item.getServiceId() == com.orvid.vk.Vk.SERVICE_ID ? "VK"
                : Sources.byService(item.getServiceId()) != null ? Sources.byService(item.getServiceId()).name : "";
        if (sub != null) {
            if (mode == GRID) sub.setText(Ui.joinDot(item.getUploaderName(), source));
            else if (mode == COMPACT) {
                sub.setText(Ui.joinDot(item.getUploaderName(), source));
                String more = Ui.joinDot(views, date);
                TextView sub2 = view.findViewById(R.id.sub2);
                if (sub2 != null) {
                    sub2.setText(more);
                    sub2.setVisibility(more.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
            else sub.setText(Ui.joinDot(item.getUploaderName(), source, views, date));
        }

        boolean live = item.getStreamType() == StreamType.LIVE_STREAM
                || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
        String badge = live ? context.getString(R.string.live) : Ui.duration(item.getDuration());
        duration.setText(badge);
        duration.setVisibility(badge.isEmpty() ? View.GONE : View.VISIBLE);
        android.widget.ImageView sourceIcon = view.findViewById(R.id.source_icon);
        if (sourceIcon != null) {
            sourceIcon.setImageResource(sourceIcon(item.getServiceId()));
            int sid = item.getServiceId();
            sourceIcon.setImageTintList(sid == com.orvid.dm.Dailymotion.SERVICE_ID || sid == com.orvid.ia.Archive.SERVICE_ID
                    ? android.content.res.ColorStateList.valueOf(0xFFFFFFFF) : null);
            sourceIcon.setVisibility(View.VISIBLE);
        }
        if (thumb != null) {
            OrvidApp.get().images().load(thumb, Ui.thumbnail(item.getThumbnails(), thumbWidth), thumbWidth, item.getUrl());
        }
        TextView avatar = view.findViewById(R.id.avatar);
        if (avatar != null) {
            String who = item.getUploaderName() == null || item.getUploaderName().trim().isEmpty()
                    ? (source.isEmpty() ? "YouTube" : source) : item.getUploaderName().trim();
            avatar.setText(who.substring(0, 1).toUpperCase());
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            float hue = Math.floorMod(who.hashCode(), 360);
            dot.setColor(android.graphics.Color.HSVToColor(new float[] {hue, 0.45f, 0.62f}));
            avatar.setBackground(dot);
        }
        view.setOnClickListener(v -> onPick.pick(item));
        view.setOnLongClickListener(thumb == null || previewer == null ? null : v -> {
            if (!(thumb.getParent() instanceof android.widget.FrameLayout)) return false;
            previewing = true;
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            previewer.start((android.widget.FrameLayout) thumb.getParent(), item);
            return true;
        });
        view.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN && idle) {
                String id = Visionos.videoId(item.getUrl());
                String page = item.getUrl();
                if (id != null || com.orvid.play.Resolve.preparable(page)) {
                    Runnable warm = id != null ? () -> Visionos.prepare(id) : () -> com.orvid.play.Resolve.prepare(page);
                    v.setTag(R.id.prepare_job, warm);
                    v.setTag(R.id.prepare_down, new float[]{e.getX(), e.getY()});
                    v.postDelayed(warm, PREPARE_DELAY_MS);
                }
            } else if (action == android.view.MotionEvent.ACTION_MOVE || action == android.view.MotionEvent.ACTION_CANCEL) {
                Object warm = v.getTag(R.id.prepare_job);
                Object down = v.getTag(R.id.prepare_down);
                boolean moved = down instanceof float[] && Math.hypot(e.getX() - ((float[]) down)[0],
                        e.getY() - ((float[]) down)[1]) > slop(v);
                if (warm instanceof Runnable && (action == android.view.MotionEvent.ACTION_CANCEL || moved)) {
                    v.removeCallbacks((Runnable) warm);
                    v.setTag(R.id.prepare_job, null);
                }
            }
            if ((action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) && previewing) {
                previewing = false;
                previewer.stop();
            }
            return false;
        });
    }
}
