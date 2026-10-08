import unittest
from build_sealed_index import build, candidate_languages, parse_official_boosters, add_official_one_piece, attach_catalogue_images
from build_card_index import sealed_item, japanese_name_aliases

class SealedIndexTest(unittest.TestCase):
    def test_shared_japanese_international_names_do_not_hide_german_products(self):
        names=['Black Bolt Booster', 'White Flare Elite Trainer Box',
               'Black Bolt & White Flare: Unova Mini Tin Display',
               'Black Bolt JP Booster Box', 'White Flare JP Deluxe Booster',
               'Black Bolt SV11B Booster Box']
        products=[{'idProduct':i+1,'name':n,'categoryName':'Pokémon Sealed'} for i,n in enumerate(names)]
        rows=build('POKEMON',products,[],{'Black Bolt':'Schwarze Blitze','White Flare':'Weiße Flammen'})['items']
        for row in rows[:3]:
            self.assertEqual(['EN','DE'],row['candidateLanguages'])
            self.assertNotIn('SV11B',row['aliases'])
            self.assertNotIn('SV11W',row['aliases'])
            self.assertNotIn('ブラックボルト',row['aliases'])
        self.assertIn('Schwarze Blitze',rows[0]['aliases'])
        self.assertIn('Weiße Flammen',rows[1]['aliases'])
        self.assertTrue({'Schwarze Blitze','Weiße Flammen'}.issubset(rows[2]['aliases']))
        for row in rows[3:]:
            self.assertEqual(['JA'],row['candidateLanguages'])
            self.assertNotIn('Schwarze Blitze',row['aliases'])
            self.assertNotIn('Weiße Flammen',row['aliases'])
        self.assertEqual(['JA'],rows[3]['languages'])
        attach_catalogue_images({'items':rows},{'items':[[100,'Black Bolt Booster Box',1,100,'JA'],[101,'Black Bolt Booster Pack',2,10,'EN']]})
        self.assertIn('/100_',rows[3]['imageUrls']['JA'])
        self.assertIn('/101_',rows[0]['imageUrls']['EN'])
        self.assertNotIn('DE',rows[0]['imageUrls'])

    def test_localized_aliases_keep_full_set_identity(self):
        localized={'Evolutions':'Evolution','Prismatic Evolutions':'Prismatische Entwicklungen',
                   'Mega Evolution':'Mega-Entwicklung','BREAKthrough':'TURBOstart'}
        rows=build('POKEMON',[{'idProduct':1,'name':'Prismatic Evolutions Booster Box','categoryName':'Pokémon Display'},
                              {'idProduct':2,'name':'BREAKthrough: Mega Evolution Three Pin 3-Pack Blister','categoryName':'Pokémon Blister'},
                              {'idProduct':3,'name':'Mega Evolution Booster Box','categoryName':'Pokémon Display'}],[],localized)['items']
        self.assertIn('Prismatische Entwicklungen',rows[0]['aliases'])
        self.assertNotIn('Evolution',rows[0]['aliases'])
        self.assertIn('TURBOstart',rows[1]['aliases'])
        self.assertNotIn('Mega-Entwicklung',rows[1]['aliases'])
        self.assertIn('Mega-Entwicklung',rows[2]['aliases'])

    def test_publisher_japanese_op14_exists_without_cardmarket_non_english_row(self):
        html='''<li class="linkListColBox" data-cat="boosters"><a href="/products/boosters/op14.php"><img data-src="/op14-pack.webp"><h4 class="linkListColTitle">ブースターパック 蒼海の七傑【OP-14】</h4><time datetime="2025-11-22"></time></a></li>'''
        entries=parse_official_boosters(html, today='2026-10-07')
        self.assertEqual('OP14',entries[0]['code'])
        self.assertEqual([],parse_official_boosters(html,today='2025-01-01'))
        index=build('ONE_PIECE',[{'idProduct':864452,'name':"The Azure Sea's Seven Booster Box",'categoryName':'One Piece Booster Boxes'}],[])
        add_official_one_piece(index,entries)
        japanese=[r for r in index['items'] if r['candidateLanguages']==['JA']]
        self.assertEqual(2,len(japanese))
        self.assertTrue(all("The Azure Sea's Seven" in r['name'] and 'OP14' in r['aliases'] and '蒼海の七傑' in r['aliases'] for r in japanese))
        self.assertTrue(all(r['referencePrice'] is None for r in japanese))
        pack=next(r for r in japanese if r['name'].endswith('Pack'))
        box=next(r for r in japanese if r['name'].endswith('Box'))
        self.assertTrue(pack['imageUrl'].endswith('op14-pack.webp'))
        self.assertIsNone(box['imageUrl'])
        ids=[r['productId'] for r in japanese]
        add_official_one_piece(index,entries)
        self.assertEqual(ids,[r['productId'] for r in index['items'] if r['candidateLanguages']==['JA']])

    def test_images_are_attached_only_to_the_same_printed_language_and_unit(self):
        index=build('POKEMON',[{'idProduct':784949,'name':'Surging Sparks Booster Box','categoryName':'Pokémon Display'},
                              {'idProduct':784950,'name':'Surging Sparks Booster','categoryName':'Pokémon Booster'}],[])
        native={'items':[[100,'Surging Sparks Booster Box',1,20,'EN'],[101,'Surging Sparks Booster Pack',1,2,'EN']]}
        attach_catalogue_images(index,native)
        self.assertIn('/100_',index['items'][0]['imageUrls']['EN'])
        self.assertIn('/101_',index['items'][1]['imageUrls']['EN'])
        self.assertNotIn('DE',index['items'][0]['imageUrls'])
        half=build('POKEMON',[{'idProduct':784951,'name':'Surging Sparks Booster Box (18 Packs)','categoryName':'Pokémon Display'}],[])
        attach_catalogue_images(half,native)
        self.assertNotIn('imageUrls',half['items'][0])

    def test_second_premium_booster_is_not_tagged_as_the_first(self):
        index=build('ONE_PIECE',[{'idProduct':900,'name':'One Piece Card The Best vol.2 Booster Box (Non-English)','categoryName':'One Piece Booster Boxes'}],[])
        self.assertIn('PRB02',index['items'][0]['aliases'])
        self.assertNotIn('PRB01',index['items'][0]['aliases'])

    def test_one_piece_is_not_cloned_into_german_and_non_english_is_only_a_candidate(self):
        products=[{"idProduct":753001,"name":"Two Legends Booster Box","categoryName":"One Piece Booster Boxes"},
                  {"idProduct":766868,"name":"Two Legends Booster Box (Non-English)","categoryName":"One Piece Booster Boxes"}]
        rows=build("ONE_PIECE",products,[])["items"]
        self.assertEqual(["EN"],rows[0]["candidateLanguages"])
        self.assertEqual(["JA"],rows[1]["candidateLanguages"])
        self.assertEqual([],rows[1]["languages"])
        self.assertTrue(all("DE" not in row["candidateLanguages"] for row in rows))

    def test_pokemon_native_sets_and_explicit_other_languages_are_kept_apart(self):
        self.assertEqual(["JA"],candidate_languages("POKEMON","Terastal Festival ex Booster Box",["SV8a"]))
        self.assertEqual(["ZH"],candidate_languages("POKEMON","Terastal Festival ex Chinese Gift Box",["SV8a"]))
        self.assertEqual(["EN","DE"],candidate_languages("POKEMON","Surging Sparks Booster Box",["Stürmische Funken"]))
        self.assertEqual(["EN"],candidate_languages("POKEMON","151 English Booster Bundle",[]))
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
