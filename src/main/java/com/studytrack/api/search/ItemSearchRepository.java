package com.studytrack.api.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * Item search with native SQL. On PostgreSQL it uses the {@code study_track.questions.search_vector} full-text
 * column (V8) with prefix matching and {@code ts_rank} ordering. Other databases (H2 in tests) have no
 * tsvector, so every word must appear (as a substring) in the title, question, answer or notes, with
 * title matches first.
 */
@Repository
public class ItemSearchRepository {
    private static final String SELECT_COLUMNS = """
            select i.id, i.title, i.question_type, i.difficulty, coalesce(p.status, 'NOT_STARTED') as status,
                   s.id as subject_id, s.name as subject_name, t.id as topic_id, t.name as topic_name,
                   i.question_html, i.answer_plain_text, i.notes_html
            """;
    private static final String FROM_OWNED_ITEMS = """
            from study_track.questions i
            join study_track.topics t on t.id = i.topic_id
            join study_track.subjects s on s.id = t.subject_id
            left join study_track.question_progress p on p.question_id = i.id and p.user_id = :userId
            where s.user_id = :userId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean fullTextSearch;

    public ItemSearchRepository(NamedParameterJdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.fullTextSearch = isPostgreSql(dataSource);
    }

    /** Raw row of a match; HTML columns are turned into a snippet by the service. */
    public record SearchRow(
            Long itemId,
            String title,
            String itemType,
            String difficulty,
            String status,
            Long subjectId,
            String subjectName,
            Long topicId,
            String topicName,
            String questionHtml,
            String answerPlain,
            String notesHtml) {
    }

    public List<SearchRow> findMatchingItems(long userId, Long subjectId, SearchTerms terms, int limit, long offset) {
        MapSqlParameterSource parameters = buildParameters(userId, subjectId, terms)
                .addValue("limit", limit)
                .addValue("offset", offset);
        String sql = SELECT_COLUMNS + FROM_OWNED_ITEMS + buildFilter(subjectId, terms)
                + buildOrder(terms) + " limit :limit offset :offset";
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new SearchRow(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("question_type"),
                resultSet.getString("difficulty"),
                resultSet.getString("status"),
                resultSet.getLong("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getLong("topic_id"),
                resultSet.getString("topic_name"),
                resultSet.getString("question_html"),
                resultSet.getString("answer_plain_text"),
                resultSet.getString("notes_html")));
    }

    public long countMatchingItems(long userId, Long subjectId, SearchTerms terms) {
        String sql = "select count(*) " + FROM_OWNED_ITEMS + buildFilter(subjectId, terms);
        Long count = jdbcTemplate.queryForObject(sql, buildParameters(userId, subjectId, terms), Long.class);
        return count == null ? 0 : count;
    }

    private MapSqlParameterSource buildParameters(long userId, Long subjectId, SearchTerms terms) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId);
        if (subjectId != null) {
            parameters.addValue("subjectId", subjectId);
        }
        if (fullTextSearch) {
            parameters.addValue("tsQuery", terms.toPrefixTsQuery());
        } else {
            for (int index = 0; index < terms.words().size(); index++) {
                // Terms hold only letters and digits, so they need no LIKE escaping.
                parameters.addValue("term" + index, "%" + terms.words().get(index) + "%");
            }
        }
        return parameters;
    }

    private String buildFilter(Long subjectId, SearchTerms terms) {
        StringBuilder filter = new StringBuilder(subjectId == null ? "" : " and s.id = :subjectId");
        if (fullTextSearch) {
            filter.append(" and i.search_vector @@ to_tsquery('english', :tsQuery)");
            return filter.toString();
        }
        for (int index = 0; index < terms.words().size(); index++) {
            String term = ":term" + index;
            filter.append(" and (lower(i.title) like ").append(term)
                    .append(" or lower(coalesce(i.question_html, '')) like ").append(term)
                    .append(" or lower(coalesce(i.answer_plain_text, '')) like ").append(term)
                    .append(" or lower(coalesce(i.notes_html, '')) like ").append(term).append(')');
        }
        return filter.toString();
    }

    private String buildOrder(SearchTerms terms) {
        if (fullTextSearch) {
            return " order by ts_rank(i.search_vector, to_tsquery('english', :tsQuery)) desc, i.updated_at desc, i.id";
        }
        return " order by case when lower(i.title) like :term0 then 0 else 1 end, i.updated_at desc, i.id";
    }

    private static boolean isPostgreSql(DataSource dataSource) {
        try {
            String product = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
            return "PostgreSQL".equalsIgnoreCase(product);
        } catch (MetaDataAccessException exception) {
            throw new IllegalStateException("Could not read the database product name", exception);
        }
    }

    /** Whether search uses PostgreSQL full-text search (false on H2). */
    public boolean isFullTextSearch() {
        return fullTextSearch;
    }
}
