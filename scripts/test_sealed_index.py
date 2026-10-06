import unittest
from build_sealed_index import build
from build_card_index import sealed_item, japanese_name_aliases

class SealedIndexTest(unittest.TestCase):
    def test_regional_reference_never_claims_a_printed_language(self):
        products=[{"idProduct":10,"name":"Terastal Festival ex Booster Box","categoryName":"Pokémon Display"},
                  {"idProduct":11,"name":"Pokemon Coin","categoryName":"Pokémon Coins"}]
        index=build("POKEMON",products,[{"idProduct":10,"trend":89.13}])
        self.assertEqual(1,len(index["items"]))
        row=index["items"][0]
        self.assertEqual(-10,row["productId"])
        self.assertEqual(89.13,row["referencePrice"])
        self.assertNotIn("price",row)
        self.assertNotIn("language",row)
        self.assertIn("SV8a",row["aliases"])
    def test_explicit_japanese_and_source_type_are_preserved(self):
        p={"productId":20,"name":"Japanese Booster Box"}
        row=sealed_item(p,100,{20:{"Normal":90}},{20:{"Normal":"TCGplayer mid (asking)"}})
        self.assertEqual("JA",row[4]);self.assertEqual("TCGplayer mid (asking)",row[5])
    def test_japanese_aliases_use_native_codes_and_abstain_on_ambiguous_names(self):
        data={"groups":{"1":["SV8a: Terastal Fest ex","SV8a",3],"2":["M5: Future Set","M5",1]},
              "cards":[["025/187","Pikachu - 025/187",1],["026/187","Raichu",1],
                       ["026/187","Another Pokemon",1],["001/100","Bulbasaur",2]]}
        aliases=japanese_name_aliases(data)
        self.assertEqual(["Pikachu"],aliases["SV8A"]["cards"]["25"])
        self.assertNotIn("26",aliases["SV8A"]["cards"])
        self.assertIn("Terastal Fest ex",aliases["SV8A"]["setAliases"])
        self.assertEqual(["Bulbasaur"],aliases["M5"]["cards"]["1"])
    def test_accessories_are_not_sealed_products(self):
        self.assertIsNone(sealed_item({"productId":30,"name":"Pokemon Deck Box"},100,{}))

if __name__=="__main__":unittest.main()
