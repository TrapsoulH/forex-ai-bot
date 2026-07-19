package com.forexbot.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * DNS MX lookup validator. Checks the email domain can actually receive mail.
 * Uses Google (8.8.8.8) then Cloudflare (1.1.1.1) as fallback DNS servers.
 * Fail-open: if DNS is unreachable, returns true so a network blip never blocks a real user.
 * Set app.validation.email-domain.enabled=false in test profile to skip lookups.
 */
@Slf4j
@Component
public class EmailDomainValidator implements ConstraintValidator<ValidEmailDomain, String> {

    @Value("${app.validation.email-domain.enabled:true}")
    private boolean enabled;

    private static final String DNS_PRIMARY   = "8.8.8.8";
    private static final String DNS_FALLBACK  = "1.1.1.1";
    private static final String TIMEOUT_MS    = "3000";
    private static final String RETRIES       = "1";
    private static final String[] DNS_SERVERS = { DNS_PRIMARY, DNS_FALLBACK };

    @Override
    public boolean isValid(String email, ConstraintValidatorContext ctx) {
        if (!enabled) return true;
        if (email == null || email.isBlank()) return true; // let @NotBlank handle blank

        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return false;

        String domain = email.substring(at + 1).toLowerCase().trim();
        return hasMxRecords(domain);
    }

    private boolean hasMxRecords(String domain) {
        for (String dns : DNS_SERVERS) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial",      "com.sun.jndi.dns.DnsContextFactory");
                env.put("java.naming.provider.url",         "dns://" + dns);
                env.put("com.sun.jndi.dns.timeout.initial", TIMEOUT_MS);
                env.put("com.sun.jndi.dns.timeout.retries", RETRIES);

                InitialDirContext ctx   = new InitialDirContext(env);
                Attributes        attrs = ctx.getAttributes(domain, new String[]{"MX"});
                Attribute         mx    = attrs.get("MX");
                ctx.close();

                if (mx != null && mx.size() > 0) {
                    log.debug("[EmailDomain] {} → {} MX record(s) — valid", domain, mx.size());
                    return true;
                }
            } catch (NamingException e) {
                log.debug("[EmailDomain] MX lookup failed for {} via {}: {}", domain, dns, e.getMessage());
            }
        }

        // No MX found — fall back to A/AAAA
        return hasARecord(domain);
    }

    private boolean hasARecord(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial",      "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url",         "dns://" + DNS_PRIMARY);
            env.put("com.sun.jndi.dns.timeout.initial", TIMEOUT_MS);
            env.put("com.sun.jndi.dns.timeout.retries", RETRIES);

            InitialDirContext ctx   = new InitialDirContext(env);
            Attributes        attrs = ctx.getAttributes(domain, new String[]{"A", "AAAA"});
            boolean           found = attrs.get("A") != null || attrs.get("AAAA") != null;
            ctx.close();
            if (!found) log.warn("[EmailDomain] {} has no MX and no A record — rejecting", domain);
            return found;

        } catch (NameNotFoundException e) {
            log.warn("[EmailDomain] {} does not exist (NXDOMAIN) — rejecting", domain);
            return false;
        } catch (NamingException e) {
            log.warn("[EmailDomain] A/AAAA lookup failed for {} — failing open: {}", domain, e.getMessage());
            return true; // fail-open
        }
    }
}
