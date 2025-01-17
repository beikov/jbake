package org.jbake.app;

import com.orientechnologies.orient.core.db.record.OTrackedMap;
import org.apache.commons.io.FilenameUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jbake.model.DocumentModel;
import org.jbake.model.ModelAttributes;
import org.jbake.model.DocumentTypes;
import org.jbake.util.DataFileUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class CrawlerTest extends ContentStoreIntegrationTest {

    @Test
    public void crawl() {
        Crawler crawler = new Crawler(db, config);
        crawler.crawl();

        Assert.assertEquals(4, db.getDocumentCount("post"));
        Assert.assertEquals(3, db.getDocumentCount("page"));

        DocumentList<DocumentModel> results = db.getPublishedPosts();

        assertThat(results.size()).isEqualTo(3);

        for (Map<String, Object> content : results) {
            assertThat(content)
                    .containsKey(ModelAttributes.ROOTPATH)
                    .containsValue("../../../");
        }

        DocumentList<DocumentModel> allPosts = db.getAllContent("post");

        assertThat(allPosts.size()).isEqualTo(4);

        for (DocumentModel content : allPosts) {
            if (content.getTitle().equals("Draft Post")) {
                assertThat(content).containsKey(ModelAttributes.DATE);
            }
        }

        // covers bug #213
        DocumentList<DocumentModel> publishedPostsByTag = db.getPublishedPostsByTag("blog");
        Assert.assertEquals(3, publishedPostsByTag.size());
    }

    @Test
    public void crawlDataFiles() {
        Crawler crawler = new Crawler(db, config);
        // manually register data doctype
        DocumentTypes.addDocumentType(config.getDataFileDocType());
        db.updateSchema();
        crawler.crawlDataFiles();
        Assert.assertEquals(2, db.getDocumentCount("data"));

        DataFileUtil dataFileUtil = new DataFileUtil(db, "data");
        Map<String, Object> videos = dataFileUtil.get("videos.yaml");
        Assert.assertFalse(videos.isEmpty());
        Assert.assertNotNull(videos.get("data"));

        // regression test for issue 747
        Map<String, Object> authorsFileContents = dataFileUtil.get("authors.yaml");
        Assert.assertFalse(authorsFileContents.isEmpty());
        Object authorsList = authorsFileContents.get("authors");
        assertThat(authorsList).isNotInstanceOf(OTrackedMap.class);
        assertThat(authorsList).isInstanceOf(LinkedHashMap.class);
        LinkedHashMap<String, Map<String, Object>> authors = (LinkedHashMap<String, Map<String, Object>>) authorsList;
        assertThat(authors.get("Joe Bloggs").get("last_name")).isEqualTo("Bloggs");
    }

    @Test
    public void renderWithPrettyUrls() {

        config.setUriWithoutExtension(true);
        config.setPrefixForUriWithoutExtension("/blog");

        Crawler crawler = new Crawler(db, config);
        crawler.crawl();

        Assert.assertEquals(4, db.getDocumentCount("post"));
        Assert.assertEquals(3, db.getDocumentCount("page"));

        DocumentList<DocumentModel> documents = db.getPublishedPosts();

        for (DocumentModel model : documents) {
            String noExtensionUri = "blog/\\d{4}/" + FilenameUtils.getBaseName(model.getFile()) + "/";

            Assert.assertThat(model.getNoExtensionUri(), RegexMatcher.matches(noExtensionUri));
            Assert.assertThat(model.getUri(), RegexMatcher.matches(noExtensionUri + "index\\.html"));
            Assert.assertThat(model.getRootPath(), is("../../../"));
        }
    }

    private static class RegexMatcher extends BaseMatcher<Object> {
        private final String regex;

        public RegexMatcher(String regex) {
            this.regex = regex;
        }

        public static RegexMatcher matches(String regex) {
            return new RegexMatcher(regex);
        }

        @Override
        public boolean matches(Object o) {
            return ((String) o).matches(regex);

        }

        @Override
        public void describeTo(Description description) {
            description.appendText("matches regex: " + regex);
        }
    }
}
