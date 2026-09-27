package com.Result_Analysis;

import com.Result_Analysis.Result_Analysis.CreditResolver;
import com.Result_Analysis.Result_Analysis.SubjectCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CreditResolverTest {

    @Autowired private CreditResolver resolver;
    @Autowired private SubjectCreditRepository repo;

    @Test void testBMATEC301() { assertEquals(3, resolver.resolveCredit("3", "BMATEC301").orElse(-1)); }
    @Test void testBEC302() { assertEquals(4, resolver.resolveCredit("3", "BEC302").orElse(-1)); }
    @Test void testBECL305() { assertEquals(1, resolver.resolveCredit("3", "BECL305").orElse(-1)); }
    @Test void testBPEK359() { assertEquals(0, resolver.resolveCredit("3", "BPEK359").orElse(-1)); }
    @Test void testBEC401() { assertEquals(3, resolver.resolveCredit("4", "BEC401").orElse(-1)); }
    @Test void testBEC402() { assertEquals(4, resolver.resolveCredit("4", "BEC402").orElse(-1)); }
    @Test void testBECL404() { assertEquals(1, resolver.resolveCredit("4", "BECL404").orElse(-1)); }
    @Test void testBYOK459() { assertEquals(0, resolver.resolveCredit("4", "BYOK459").orElse(-1)); }
    @Test void testUnknownMATH101() { assertTrue(resolver.resolveCredit("3", "MATH101").isEmpty()); }
    @Test void testUnknownXYZ() { assertTrue(resolver.resolveCredit("4", "XYZ999").isEmpty()); }
    @Test void testNormalization() {
        assertEquals(3, resolver.resolveCredit(" 3 ", " bmatec301 ").orElse(-1));
        assertEquals(4, resolver.resolveCredit("4", " bec402 ").orElse(-1));
    }
}
