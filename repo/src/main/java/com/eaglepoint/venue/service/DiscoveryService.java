package com.eaglepoint.venue.service;

import com.eaglepoint.venue.api.dto.DiscoveryResultItem;
import com.eaglepoint.venue.api.dto.PagedDiscoveryResponse;
import com.eaglepoint.venue.api.dto.SuggestionResponse;
import com.eaglepoint.venue.domain.CommunityAnnouncement;
import com.eaglepoint.venue.domain.Event;
import com.eaglepoint.venue.domain.Season;
import com.eaglepoint.venue.domain.SessionEntity;
import com.eaglepoint.venue.mapper.CommunityAnnouncementMapper;
import com.eaglepoint.venue.mapper.EventMapper;
import com.eaglepoint.venue.mapper.SeasonMapper;
import com.eaglepoint.venue.mapper.SessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DiscoveryService {
    private final EventMapper eventMapper;
    private final SeasonMapper seasonMapper;
    private final SessionMapper sessionMapper;
    private final CommunityAnnouncementMapper communityAnnouncementMapper;

    public DiscoveryService(
            EventMapper eventMapper,
            SeasonMapper seasonMapper,
            SessionMapper sessionMapper,
            CommunityAnnouncementMapper communityAnnouncementMapper
    ) {
        this.eventMapper = eventMapper;
        this.seasonMapper = seasonMapper;
        this.sessionMapper = sessionMapper;
        this.communityAnnouncementMapper = communityAnnouncementMapper;
    }

    public SuggestionResponse typeAheadSuggestions(String query) {
        String normalized = normalize(query);
        LinkedHashMap<String, String> unique = new LinkedHashMap<String, String>();
        if (normalized.isEmpty()) {
            SuggestionResponse empty = new SuggestionResponse();
            empty.setQuery(query);
            return empty;
        }

        List<DiscoveryResultItem> allItems = collectAllItems();
        for (DiscoveryResultItem item : allItems) {
            addSuggestion(unique, item.getTitle(), normalized);
            if (item.getCategory() != null) {
                addSuggestion(unique, item.getCategory(), normalized);
            }
            if (item.getAuthor() != null) {
                addSuggestion(unique, item.getAuthor(), normalized);
            }
            if (unique.size() >= 10) {
                break;
            }
        }

        SuggestionResponse response = new SuggestionResponse();
        response.setQuery(query);
        response.setSuggestions(new ArrayList<String>(unique.values()));
        return response;
    }

    public PagedDiscoveryResponse search(
            String query,
            String type,
            LocalDateTime from,
            LocalDateTime to,
            String author,
            String category,
            Integer minWords,
            Integer maxWords,
            String sort,
            Integer page,
            Integer size
    ) {
        List<DiscoveryResultItem> allItems = collectAllItems();
        String normalizedQuery = normalize(query);
        String normalizedType = normalize(type);
        String normalizedAuthor = normalize(author);
        String normalizedCategory = normalize(category);

        List<DiscoveryResultItem> filtered = new ArrayList<DiscoveryResultItem>();
        for (DiscoveryResultItem item : allItems) {
            if (!matchesType(item, normalizedType)) {
                continue;
            }
            if (!matchesText(item, normalizedQuery)) {
                continue;
            }
            if (!matchesDateRange(item, from, to)) {
                continue;
            }
            if (!matchesAuthor(item, normalizedAuthor)) {
                continue;
            }
            if (!matchesCategory(item, normalizedCategory)) {
                continue;
            }
            if (!matchesWordCount(item, minWords, maxWords)) {
                continue;
            }

            DiscoveryResultItem view = cloneItem(item);
            int relevance = scoreRelevance(view, normalizedQuery);
            view.setRelevance(relevance);
            view.setHighlightedTitle(highlight(view.getTitle(), normalizedQuery));
            view.setHighlightedSnippet(highlight(view.getSnippet(), normalizedQuery));
            filtered.add(view);
        }

        List<DiscoveryResultItem> deduped = deduplicate(filtered);
        sortResults(deduped, sort);
        return paginate(deduped, page, size);
    }

    public PagedDiscoveryResponse browseSeasons(Integer page, Integer size, String sort) {
        return search("", "SEASON", null, null, null, null, null, null, sort, page, size);
    }

    public PagedDiscoveryResponse browseSessions(Integer page, Integer size, String sort) {
        return search("", "SESSION", null, null, null, null, null, null, sort, page, size);
    }

    public PagedDiscoveryResponse browseAnnouncements(
            LocalDateTime from,
            LocalDateTime to,
            String author,
            String category,
            Integer minWords,
            Integer maxWords,
            Integer page,
            Integer size,
            String sort
    ) {
        return search("", "ANNOUNCEMENT", from, to, author, category, minWords, maxWords, sort, page, size);
    }

    private List<DiscoveryResultItem> collectAllItems() {
        List<DiscoveryResultItem> rows = new ArrayList<DiscoveryResultItem>();

        List<Event> events = eventMapper.findAll();
        for (Event event : events) {
            DiscoveryResultItem item = new DiscoveryResultItem();
            item.setType("EVENT");
            item.setId(event.getId());
            item.setTitle(event.getName());
            item.setSnippet(event.getCode());
            item.setWordCount(wordCount(event.getName()));
            item.setPopularity(0);
            item.setTimestamp(event.getStartDate().atStartOfDay());
            rows.add(item);
        }

        List<Season> seasons = seasonMapper.findAll();
        for (Season season : seasons) {
            DiscoveryResultItem item = new DiscoveryResultItem();
            item.setType("SEASON");
            item.setId(season.getId());
            item.setTitle(season.getName());
            item.setSnippet("Season of event #" + season.getEventId());
            item.setWordCount(wordCount(season.getName()));
            item.setPopularity(0);
            item.setTimestamp(season.getStartDate().atStartOfDay());
            rows.add(item);
        }

        List<SessionEntity> sessions = sessionMapper.findAll();
        for (SessionEntity session : sessions) {
            DiscoveryResultItem item = new DiscoveryResultItem();
            item.setType("SESSION");
            item.setId(session.getId());
            item.setTitle(session.getTitle());
            item.setSnippet("Session starts at " + session.getStartTime());
            item.setWordCount(wordCount(session.getTitle()));
            item.setPopularity(0);
            item.setTimestamp(session.getStartTime());
            rows.add(item);
        }

        List<CommunityAnnouncement> announcements = communityAnnouncementMapper.findAll();
        for (CommunityAnnouncement announcement : announcements) {
            DiscoveryResultItem item = new DiscoveryResultItem();
            item.setType("ANNOUNCEMENT");
            item.setId(announcement.getId());
            item.setTitle(announcement.getTitle());
            item.setSnippet(trimSnippet(announcement.getBody()));
            item.setAuthor(announcement.getAuthor());
            item.setCategory(announcement.getCategory());
            item.setWordCount(announcement.getWordCount());
            item.setPopularity(announcement.getPopularity());
            item.setTimestamp(announcement.getPublishedAt());
            rows.add(item);
        }

        return rows;
    }

    private void addSuggestion(Map<String, String> unique, String rawSuggestion, String normalizedQuery) {
        if (rawSuggestion == null) {
            return;
        }
        String value = rawSuggestion.trim();
        if (value.isEmpty()) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(normalizedQuery) || normalized.contains(normalizedQuery)) {
            unique.put(normalized, value);
        }
    }

    private boolean matchesType(DiscoveryResultItem item, String type) {
        if (type.isEmpty() || "ALL".equals(type)) {
            return true;
        }
        return type.equals(item.getType());
    }

    private boolean matchesText(DiscoveryResultItem item, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String title = normalize(item.getTitle());
        String snippet = normalize(item.getSnippet());
        return title.contains(query) || snippet.contains(query);
    }

    private boolean matchesDateRange(DiscoveryResultItem item, LocalDateTime from, LocalDateTime to) {
        LocalDateTime timestamp = item.getTimestamp();
        if (timestamp == null) {
            return true;
        }
        if (from != null && timestamp.isBefore(from)) {
            return false;
        }
        if (to != null && timestamp.isAfter(to.with(LocalTime.MAX))) {
            return false;
        }
        return true;
    }

    private boolean matchesAuthor(DiscoveryResultItem item, String author) {
        if (author.isEmpty()) {
            return true;
        }
        if (item.getAuthor() == null) {
            return false;
        }
        return normalize(item.getAuthor()).contains(author);
    }

    private boolean matchesCategory(DiscoveryResultItem item, String category) {
        if (category.isEmpty()) {
            return true;
        }
        if (item.getCategory() == null) {
            return false;
        }
        return normalize(item.getCategory()).contains(category);
    }

    private boolean matchesWordCount(DiscoveryResultItem item, Integer minWords, Integer maxWords) {
        if (minWords == null && maxWords == null) {
            return true;
        }
        if (item.getWordCount() == null) {
            return false;
        }
        if (minWords != null && item.getWordCount() < minWords) {
            return false;
        }
        if (maxWords != null && item.getWordCount() > maxWords) {
            return false;
        }
        return true;
    }

    private List<DiscoveryResultItem> deduplicate(List<DiscoveryResultItem> rows) {
        LinkedHashMap<String, DiscoveryResultItem> dedup = new LinkedHashMap<String, DiscoveryResultItem>();
        for (DiscoveryResultItem row : rows) {
            String key = row.getType() + "|" + row.getId() + "|" + normalize(row.getTitle());
            if (!dedup.containsKey(key)) {
                dedup.put(key, row);
            }
        }
        return new ArrayList<DiscoveryResultItem>(dedup.values());
    }

    private void sortResults(List<DiscoveryResultItem> rows, String sort) {
        String normalizedSort = normalize(sort);
        Comparator<DiscoveryResultItem> relevanceComparator = new Comparator<DiscoveryResultItem>() {
            @Override
            public int compare(DiscoveryResultItem a, DiscoveryResultItem b) {
                int r1 = a.getRelevance() == null ? 0 : a.getRelevance();
                int r2 = b.getRelevance() == null ? 0 : b.getRelevance();
                if (r1 != r2) {
                    return Integer.compare(r2, r1);
                }
                return compareNewest(a, b);
            }
        };

        Comparator<DiscoveryResultItem> newestComparator = new Comparator<DiscoveryResultItem>() {
            @Override
            public int compare(DiscoveryResultItem a, DiscoveryResultItem b) {
                return compareNewest(a, b);
            }
        };

        Comparator<DiscoveryResultItem> popularityComparator = new Comparator<DiscoveryResultItem>() {
            @Override
            public int compare(DiscoveryResultItem a, DiscoveryResultItem b) {
                int p1 = a.getPopularity() == null ? 0 : a.getPopularity();
                int p2 = b.getPopularity() == null ? 0 : b.getPopularity();
                if (p1 != p2) {
                    return Integer.compare(p2, p1);
                }
                return compareNewest(a, b);
            }
        };

        if ("newest".equals(normalizedSort)) {
            Collections.sort(rows, newestComparator);
            return;
        }
        if ("popularity".equals(normalizedSort)) {
            Collections.sort(rows, popularityComparator);
            return;
        }
        Collections.sort(rows, relevanceComparator);
    }

    private int compareNewest(DiscoveryResultItem a, DiscoveryResultItem b) {
        LocalDateTime t1 = a.getTimestamp();
        LocalDateTime t2 = b.getTimestamp();
        if (t1 == null && t2 == null) {
            return 0;
        }
        if (t1 == null) {
            return 1;
        }
        if (t2 == null) {
            return -1;
        }
        return t2.compareTo(t1);
    }

    private int scoreRelevance(DiscoveryResultItem item, String query) {
        if (query.isEmpty()) {
            return 0;
        }
        int score = 0;
        String title = normalize(item.getTitle());
        String snippet = normalize(item.getSnippet());
        if (title.equals(query)) {
            score += 100;
        }
        if (title.startsWith(query)) {
            score += 40;
        }
        if (title.contains(query)) {
            score += 30;
        }
        if (snippet.contains(query)) {
            score += 20;
        }
        return score;
    }

    private PagedDiscoveryResponse paginate(List<DiscoveryResultItem> rows, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, 100);

        int fromIndex = safePage * safeSize;
        int toIndex = Math.min(rows.size(), fromIndex + safeSize);

        List<DiscoveryResultItem> slice = new ArrayList<DiscoveryResultItem>();
        if (fromIndex < rows.size()) {
            slice = rows.subList(fromIndex, toIndex);
        }

        PagedDiscoveryResponse response = new PagedDiscoveryResponse();
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotal((long) rows.size());
        response.setItems(new ArrayList<DiscoveryResultItem>(slice));
        return response;
    }

    private DiscoveryResultItem cloneItem(DiscoveryResultItem source) {
        DiscoveryResultItem copy = new DiscoveryResultItem();
        copy.setType(source.getType());
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setSnippet(source.getSnippet());
        copy.setAuthor(source.getAuthor());
        copy.setCategory(source.getCategory());
        copy.setWordCount(source.getWordCount());
        copy.setPopularity(source.getPopularity());
        copy.setTimestamp(source.getTimestamp());
        return copy;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimSnippet(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 180) + "...";
    }

    private int wordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String[] tokens = text.trim().split("\\s+");
        return tokens.length;
    }

    private String highlight(String text, String query) {
        if (text == null || query == null || query.isEmpty()) {
            return text;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();

        int index = 0;
        while (true) {
            int found = lowerText.indexOf(lowerQuery, index);
            if (found < 0) {
                out.append(text.substring(index));
                break;
            }
            out.append(text.substring(index, found));
            out.append("<mark>");
            out.append(text.substring(found, found + lowerQuery.length()));
            out.append("</mark>");
            index = found + lowerQuery.length();
        }
        return out.toString();
    }
}
