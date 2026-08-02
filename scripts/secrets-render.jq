def fail($message): error($message);
def quote_wrapped:
  length >= 1 and
  ((startswith("\"") and endswith("\"")) or
   (startswith("'") and endswith("'")));
def control_character: any(explode[]; . < 32 or . == 127);
def render:
  if contains("\"") | not then
    "\"\(.)\""
  elif contains("'") | not then
    "'\(.)'"
  else
    fail("decrypted payload contains a value that cannot be rendered safely")
  end;

if type != "object" then
  fail("decrypted payload must be a JSON object")
elif keys != ["PUTIO_CLIENT_ID", "PUTIO_TOKEN_FIRST_PARTY", "PUTIO_TOKEN_THIRD_PARTY"] then
  fail("decrypted payload key inventory does not match the SDK contract")
elif any(.[]; type != "string" or length == 0 or quote_wrapped or control_character) then
  fail("decrypted payload contains an empty, non-string, quote-wrapped, or control-character value")
elif (.PUTIO_CLIENT_ID | test("^[0-9]+$") | not) then
  fail("decrypted payload contains an invalid numeric identifier")
else
  to_entries
  | sort_by(.key)
  | map("\(.key)=\(.value | render)")
  | join("\n")
end
