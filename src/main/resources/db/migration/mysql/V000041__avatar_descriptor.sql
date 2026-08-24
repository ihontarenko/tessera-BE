-- ============================================================================
--  V000041  A generated avatar is a descriptor, not a seed
-- ----------------------------------------------------------------------------
--  `avatar_preset` held a bare seed — a couple of hyphenated words — and 64
--  characters was generous for that. It now holds a descriptor:
--
--      avatar.1.<strategy>.<url-encoded seed>.<base64 parameters>
--
--  which carries the strategy that draws the face and whatever its controls
--  were set to. With parameters that is hundreds of bytes, so the column has to
--  grow. 512 is the ceiling `AvatarDescriptors` in `jmouse-avatars` states, and
--  a longer value is refused there with the number rather than truncated here.
--
--  ⚠️ NO DATA MIGRATION, AND THAT IS THE POINT.
--
--  Every existing `avatar_preset` is a bare seed, and a bare seed stays valid
--  forever: the generator every product shipped before this ships on as the
--  `pixel-classic` strategy, and a dotless value decodes into it. So nobody
--  wakes up wearing a different face — which is the whole test of whether this
--  change was done correctly.
--
--  ⚠️ The CHECK constraint is deliberately untouched. It states which of the
--  three columns may be populated for each `avatar_kind`, never what is inside
--  one; the shape of the value is the library's business and always was.
-- ============================================================================

ALTER TABLE members
    MODIFY COLUMN avatar_preset VARCHAR(512);
