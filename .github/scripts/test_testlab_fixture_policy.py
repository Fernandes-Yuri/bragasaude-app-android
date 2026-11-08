import unittest
from datetime import datetime, timezone, timedelta
from testlab_fixture_policy import validate


class FixturePolicyTest(unittest.TestCase):
    def fixture(self):
        return {"run_id": "20261108-review", "expires_at": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(),
                "accounts": [{"uid": f"robo-20261108-review-{role.lower()}", "role": role, "disposable": True}
                             for role in ["PATIENT", "CAREGIVER", "HYBRID"]],
                "allow_external_intents": False, "cleanup_inventory": ["accounts", "bindings", "messages", "vitals"]}

    def test_shared_account_and_external_effects_are_rejected(self):
        value = self.fixture()
        self.assertTrue(validate(value))
        value["accounts"][0]["uid"] = "shared-manual-test-account"
        with self.assertRaises(ValueError):
            validate(value)
        value = self.fixture()
        value["allow_external_intents"] = True
        with self.assertRaises(ValueError):
            validate(value)

    def test_expired_and_reused_fixtures_are_rejected(self):
        value = self.fixture()
        value["expires_at"] = (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat()
        with self.assertRaises(ValueError):
            validate(value)
        value = self.fixture()
        value["accounts"][1]["uid"] = value["accounts"][0]["uid"]
        with self.assertRaises(ValueError):
            validate(value)
