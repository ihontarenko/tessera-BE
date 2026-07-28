package net.innoventa.tessera.mapper;

import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.dto.configuration.StatusResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Straight field-for-field mapping of the flat configuration catalogs — the case MapStruct is for.
 * The richer, join-backed shapes ({@code Workflow} + transitions, the two scheme types + their items)
 * are hand-assembled in {@code ConfigurationService}, mirroring Moneta's hand-built responses where a
 * response needs data the entity alone doesn't carry.
 */
@Mapper(componentModel = "spring")
public interface ConfigurationMapper {

    IssueTypeResponse toResponse(IssueType issueType);

    List<IssueTypeResponse> toIssueTypeResponses(List<IssueType> issueTypes);

    PriorityResponse toResponse(Priority priority);

    List<PriorityResponse> toPriorityResponses(List<Priority> priorities);

    StatusResponse toResponse(Status status);

    List<StatusResponse> toStatusResponses(List<Status> statuses);

    ResolutionResponse toResponse(Resolution resolution);

    List<ResolutionResponse> toResolutionResponses(List<Resolution> resolutions);

}
