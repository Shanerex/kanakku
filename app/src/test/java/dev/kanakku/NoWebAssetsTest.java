package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class NoWebAssetsTest {

  @Test
  void appClasspathHasNoWebAssets() throws Exception {
    for (String path :
        new String[] {
          "static", "public", "META-INF/resources", "index.html", "static/index.html"
        }) {
      var urls = Collections.list(getClass().getClassLoader().getResources(path));
      assertThat(urls).as("classpath resource %s", path).extracting(URL::toString).isEmpty();
    }
  }
}
