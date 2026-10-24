package dev.kanakku.identity.controller;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** The opt-out for plain-HTTP local development drops the Secure flag from both cookies. */
@SpringBootTest(properties = "kanakku.auth.secure-cookies=false")
@AutoConfigureMockMvc
class InsecureCookiesOptOutTest extends SecureCookiesTest {

  @Override
  boolean secure() {
    return false;
  }
}
