"""Runtime policy constraint not checked by Google's WFF v1 XSD validator."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

FACE = Path(__file__).resolve().parents[1] / 'src/main/res/raw/watchface.xml'


def validate_policy_types(root):
    for slot in root.iter('ComplicationSlot'):
        supported = set(re.split(r'[\s,|]+', slot.attrib['supportedTypes']))
        policy = slot.find('DefaultProviderPolicy')
        if policy is not None:
            for key in ('defaultSystemProviderType', 'primaryProviderType', 'secondaryProviderType'):
                value = policy.get(key)
                if value is not None and value not in supported:
                    raise ValueError(f'{key}={value} not supported by slot {slot.get("slotId")}: {supported}')


class PolicyTests(unittest.TestCase):
    def test_production_policy_matches_supported_types(self):
        validate_policy_types(ET.parse(FACE).getroot())

    def test_empty_fallback_type_regression_is_rejected(self):
        root = ET.parse(FACE).getroot()
        root.find('.//DefaultProviderPolicy').set('defaultSystemProviderType', 'EMPTY')
        with self.assertRaises(ValueError):
            validate_policy_types(root)


if __name__ == '__main__':
    unittest.main()
