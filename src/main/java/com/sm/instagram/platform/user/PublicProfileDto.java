package com.sm.instagram.platform.user;

/**
 * A public profile is a company's or an influencer's, and the payload does not say which.
 *
 * <p>Naming the two implementations here does two things springdoc would not do on its own. It
 * fills them in: {@code InfluencerPublicProfileDto} was published as a bare {@code type: object},
 * so {@code GET /users/influencers/&#123;id&#125;/public-profile} had no contract at all and every
 * generated client typed its body as anything. And it makes the union {@code anyOf} rather than
 * {@code oneOf}.
 *
 * <p>{@code oneOf} asserts a body matches exactly one branch. Neither of these declared a required
 * property, so every company profile also validated as an influencer profile, and the fuzzer
 * reported {@code /users/paged/public-profile} as returning something the document could not parse.
 * The document was claiming a distinction the API did not make.
 *
 * <p>So the API makes it: {@code profileType} is on the wire, constant per implementation and
 * constrained to that constant in each branch, which is what a consumer needs to dispatch on
 * instead of guessing from whichever optional fields happen to be present. OpenAPI's
 * {@code discriminator} alone would not have been enough -- a JSON Schema validator ignores it,
 * and the fuzzer is one.
 */
@io.swagger.v3.oas.annotations.media.Schema(
        oneOf = {CompanyPublicProfileDto.class, InfluencerPublicProfileDto.class},
        discriminatorProperty = "profileType")
public sealed interface PublicProfileDto
        permits InfluencerPublicProfileDto, CompanyPublicProfileDto {
}


