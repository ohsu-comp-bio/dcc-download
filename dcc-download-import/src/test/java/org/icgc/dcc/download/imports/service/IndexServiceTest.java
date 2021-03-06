/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.download.imports.service;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.icgc.dcc.common.core.json.Jackson.asObjectNode;
import static org.icgc.dcc.common.test.json.JsonNodes.$;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import lombok.val;

import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.node.Node;
import org.icgc.dcc.download.imports.core.DownloadImportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tlrx.elasticsearch.test.annotations.ElasticsearchClient;
import com.github.tlrx.elasticsearch.test.annotations.ElasticsearchNode;
import com.github.tlrx.elasticsearch.test.support.junit.runners.ElasticsearchRunner;
import com.google.common.collect.Lists;

@RunWith(ElasticsearchRunner.class)
public class IndexServiceTest {

  private static final ObjectNode DONOR_MAPPING = asObjectNode($("{donor:{_source:{compress:true}}}"));
  private static final ObjectNode SETTINGS = asObjectNode($("{'index.store.compress.stored':true}"));
  private static final String INDEX_NAME = "icgc21-test";
  private static final String INDEX_TYPE = "donor";

  @ElasticsearchNode
  Node node;
  @ElasticsearchClient
  private Client client;

  private IndexService service;

  @Before
  public void before() {
    service = new IndexService(INDEX_NAME, client);
    service.applySettings(SETTINGS);
  }

  @After
  public void tearDown() throws Exception {
    client.admin().indices()
        .delete(new DeleteIndexRequest(INDEX_NAME))
        .actionGet();
  }

  @Test(expected = DownloadImportException.class)
  public void indexServiceTest_duplicateSettings() {
    service.applySettings(SETTINGS);
  }

  @Test
  public void indexServiceTest() throws Exception {
    service.applyMapping(INDEX_TYPE, DONOR_MAPPING);

    val indexClient = client.admin().indices();
    verifySettings(indexClient);
    verifyMapping(indexClient);
  }

  @Test
  public void aliasIndexTest() {
    val aliasName = "icgc-release";
    val nextIndexName = "icgc22-test";

    // Create alias
    val indexClient = client.admin().indices();
    checkState(indexClient.prepareAliases()
        .addAlias(INDEX_NAME, aliasName)
        .execute()
        .actionGet()
        .isAcknowledged(), "Failed to create alias");

    // Create new index
    service = new IndexService(nextIndexName, client);
    service.applySettings(SETTINGS);

    // Create alias to new index
    service.aliasIndex();

    // Verify
    val indicesToAliases = indexClient.getAliases(new GetAliasesRequest(aliasName)).actionGet().getAliases();
    val indicesContainer = indicesToAliases.keysIt();

    val indexNames = Lists.<String> newArrayList();
    indicesContainer.forEachRemaining(indexNames::add);
    assertThat(indexNames).containsOnly(nextIndexName);
  }

  private void verifyMapping(IndicesAdminClient indexClient) throws Exception {
    val mappingResponse = indexClient.getMappings(new GetMappingsRequest().indices(INDEX_NAME).types(INDEX_TYPE)).get();
    val mappings = mappingResponse.mappings();
    val indexMeta = mappings.get(INDEX_NAME);
    val typeMeta = indexMeta.get("donor").sourceAsMap();
    @SuppressWarnings("unchecked")
    val sourceMeta = (Map<String, Object>) typeMeta.get("_source");
    assertThat((boolean) sourceMeta.get("compress")).isTrue();
  }

  private void verifySettings(IndicesAdminClient indexClient) throws InterruptedException, ExecutionException {
    val settingsResponse = indexClient.getSettings(new GetSettingsRequest().indices(INDEX_NAME)).get();
    val setting = settingsResponse.getSetting(INDEX_NAME, "index.store.compress.stored");
    assertThat(setting).isEqualTo("true");
  }

}
