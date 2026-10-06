import unittest
from pregrade.validate_phone_dataset import evaluate

class PhoneValidationTest(unittest.TestCase):
    def row(self,**fields):
        return dict(certificate="PSA:1",split="test",grade=8,predicted=None,low=7,high=9,game="POKEMON",language="JA",device="phone",certificateVerified=True,**fields)
    def test_range_only_results_are_evaluated_without_invented_point_grades(self):
        result=evaluate([self.row(),self.row()])
        self.assertEqual(1,result["physical_test_cards"])
        self.assertEqual(0,result["coverage"])
        self.assertEqual(1,result["range_coverage"])
        self.assertEqual(2,result["mean_range_width"])
        self.assertIsNone(result["exact_accuracy"])
        self.assertFalse(result["validated_for_release"])
        self.assertEqual(1,result["strata"]["language"]["JA"]["physical_test_cards"])
    def test_leakage_and_conflicting_labels_are_rejected(self):
        for change in [dict(split="train"),dict(grade=9)]:
            modified=self.row();modified.update(change)
            with self.assertRaises(ValueError):evaluate([self.row(),modified])
    def test_missing_high_endpoint_is_rejected(self):
        row=self.row();row["high"]=None
        with self.assertRaises(ValueError):evaluate([row])
